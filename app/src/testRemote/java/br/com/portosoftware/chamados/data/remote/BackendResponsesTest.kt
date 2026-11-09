package br.com.portosoftware.chamados.data.remote

import android.app.Application
import android.net.Uri
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.NewApplicant
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.PendingAttachment
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.Role
import br.com.portosoftware.chamados.core.model.ThemeMode
import br.com.portosoftware.chamados.core.model.UsedStockItem
import br.com.portosoftware.chamados.data.remote.network.CsrfInterceptor
import br.com.portosoftware.chamados.data.remote.network.PersistentCookieJar
import br.com.portosoftware.chamados.data.remote.network.RemoteSession
import br.com.portosoftware.chamados.data.remote.network.SessionHeadersInterceptor
import br.com.portosoftware.chamados.data.remote.network.TokenRefreshAuthenticator
import br.com.portosoftware.chamados.di.NetworkModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartReader
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * As respostas do backend de verdade, gravadas em `contracts/backend/responses.json` pelo teste de contrato do backend
 * (`RealBackendContractTest`), lidas pelo app. Cada caso é refeito pela pilha HTTP do app (cookies, CSRF, cabeçalhos
 * da sessão e renovação em 401) e pelos repositórios, contra um servidor local que devolve a resposta gravada, com os
 * cookies dela. O teste confere, caso a caso:
 * - que o app manda a requisição que o backend recebeu: método, caminho com a query, e o corpo JSON (comparado como
 *   JSON) ou o nome, o arquivo e o tamanho de cada parte do multipart. Onde o roteiro do backend manda o que o app não
 *   manda, como um corpo vazio para provar o 400, o caso fica marcado ([Case.sends]) e só a resposta é conferida;
 * - que a resposta chega ao domínio: sucesso em 2xx; em erro, o [DomainError] do status, com a mensagem e os campos
 *   do corpo, como a tela os mostra.
 * Os cabeçalhos (CSRF, cookies e secretaria ativa) não são comparados: o `ApiWireTest` confere cada um.
 * E confere que cada rota da [ChamadosApi], mais o refresh, tem um caso de sucesso gravado, e que cada arquivo tem
 * uma entrada em [cases]. Arquivo novo sem entrada derruba o teste.
 *
 * Os arquivos mudam quando o backend muda o que responde: veja o README, "Contrato da API".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackendResponsesTest {
    private val context by lazy { RuntimeEnvironment.getApplication() }

    /** Como refazer cada caso pelo app. A chave é o nome do arquivo, sem o `.json`. */
    private val cases: Map<String, Case> =
        mapOf(
            "auth-csrf" to Case { apiCall { api.csrf().successOrThrow() } },
            "auth-login-invalid" to Case(sends = false) { auth.login(ADMIN, PASSWORD) },
            "auth-login-wrong-password" to
                Case(expected = { AppResult.Failure(DomainError.Validation("E-mail ou senha inválidos.")) }) {
                    auth.login(ADMIN, "outra-senha")
                },
            "auth-login-without-csrf" to Case { auth.login(ADMIN, PASSWORD) },
            "auth-me-anonymous" to Case { apiCall { api.me().toDomain() } },
            "auth-login" to
                Case(serve = listOf("auth-me"), expected = { AppResult.Success(Role.ADMIN) }) {
                    auth.login(ADMIN, PASSWORD)
                    signedInRole()
                },
            "auth-me" to Case { apiCall { api.me().toDomain() } },
            "auth-refresh" to
                Case(signedIn = true, serve = listOf("auth-me-anonymous", "auth-me")) { apiCall { api.me().toDomain() } },
            "auth-refresh-without-cookie" to
                Case(signedIn = true, serve = listOf("auth-me-anonymous")) { apiCall { api.me().toDomain() } },
            "auth-forgot-password" to Case { auth.requestPasswordReset(ADMIN) },
            "auth-forgot-password-invalid" to Case { auth.requestPasswordReset(body.text("email")) },
            "dashboard" to Case(signedIn = true) { calls.dashboard() },
            "dashboard-without-secretary" to Case { calls.dashboard() },
            "calls-filter" to Case(signedIn = true) { calls.search(CallFilter(), 0, 20) },
            "calls-filter-by-status" to
                Case(signedIn = true) {
                    val filter =
                        CallFilter(
                            statuses = setOf(CallStatus.OPENED, CallStatus.IN_PROGRESS),
                            priority = CallPriority.HIGH,
                            unitId = query("unitId"),
                        )
                    calls.search(filter, 0, 20)
                },
            "calls-filter-invalid-status" to Case(signedIn = true, sends = false) { calls.search(CallFilter(), 0, 20) },
            "call-full" to Case(signedIn = true) { calls.detail(segment(1)) },
            "call-in-progress" to Case(signedIn = true) { calls.detail(segment(1)) },
            "call-minimal" to Case(signedIn = true) { calls.detail(segment(1)) },
            "call-without-requester" to Case(signedIn = true) { calls.detail(segment(1)) },
            "call-other-secretary" to Case(signedIn = true) { calls.detail(segment(1)) },
            "units" to Case(signedIn = true) { catalog.units(segment(2)) },
            "locals" to Case(signedIn = true) { catalog.locals(segment(2)) },
            "equipment" to Case(signedIn = true) { catalog.equipment(segment(2)) },
            "equipment-empty" to
                Case(signedIn = true, expected = { AppResult.Success(emptyList<Any>()) }) { catalog.equipment(segment(2)) },
            "categories" to Case(signedIn = true) { catalog.categories() },
            "subcategories" to Case(signedIn = true) { catalog.subcategories(segment(2)) },
            "technicians" to Case(signedIn = true) { catalog.technicians() },
            "applicants-search" to Case(signedIn = true) { applicants.search(query("q")) },
            "positions" to Case(signedIn = true) { catalog.positions() },
            "stock" to Case(signedIn = true) { stock.items("") },
            "position-create" to Case(signedIn = true) { catalog.createPosition(body.text("name")) },
            "position-create-invalid" to Case(signedIn = true, sends = false) { catalog.createPosition("") },
            "position-create-without-csrf" to Case(signedIn = true) { catalog.createPosition(body.text("name")) },
            "applicant-create" to Case(signedIn = true) { applicants.create(newApplicant()) },
            "applicant-create-invalid" to Case(signedIn = true) { applicants.create(newApplicant()) },
            "call-create" to Case(signedIn = true) { calls.create(newCall()) },
            "call-create-invalid" to Case(signedIn = true, sends = false) { calls.create(newCall(of = "call-create")) },
            "call-progress" to Case(signedIn = true) { calls.registerProgress(segment(1), progress()) },
            "call-progress-invalid" to
                Case(signedIn = true, sends = false) {
                    calls.registerProgress(segment(1), ProgressRequest("Sem corpo.", CallStatus.IN_PROGRESS, null, emptyList()))
                },
            "call-attachments" to Case(signedIn = true) { calls.uploadAttachments(segment(1), attachments()) },
            // Gravado sem o Content-Length da parte, que o OkHttp manda. Com ele, o Tomcat responde o 413 sem ler o arquivo e
            // fecha a conexão: o OkHttp lê o 413, mas o HttpClient do teste do backend nem sempre. Como o app recusa antes
            // o arquivo acima de 10 MB, o caso manda um pequeno só para receber a resposta gravada.
            "call-attachments-too-large" to
                Case(signedIn = true, sends = false) {
                    calls.uploadAttachments(segment(1), attachments(name = "video.mp4", content = "pequeno"))
                },
            "call-attachments-closed" to Case(signedIn = true) { calls.uploadAttachments(segment(1), attachments()) },
            "call-created" to Case(signedIn = true) { calls.detail(segment(1)) },
            "auth-logout" to
                Case(signedIn = true, expected = { AppResult.Success(SessionState.SignedOut) }) {
                    auth.logout()
                    AppResult.Success(session.state.value)
                },
            "auth-me-viewer" to
                Case(expected = { AppResult.Success(Role.VIEWER) }) {
                    apiCall { api.me().toDomain().role }
                },
            "dashboard-viewer" to Case(signedIn = true) { calls.dashboard() },
            "calls-filter-other-team" to Case(signedIn = true) { calls.search(CallFilter(), 0, 20) },
        )

    @Test
    fun `cada arquivo gravado tem como refazer o caso pelo app, e o contrario`() {
        assertEquals(cases.keys.sorted(), recordings.keys.sorted())
    }

    @Test
    fun `cada rota que o app usa tem um caso de sucesso gravado`() {
        val annotated =
            ChamadosApi::class.java.declaredMethods.mapNotNull { method ->
                method.getAnnotation(GET::class.java)?.let { "GET /${it.value}" }
                    ?: method.getAnnotation(POST::class.java)?.let { "POST /${it.value}" }
                    ?: method.getAnnotation(PATCH::class.java)?.let { "PATCH /${it.value}" }
            }
        assertEquals("a ChamadosApi mudou de forma: confira a leitura das anotações", 22, annotated.size)
        // O refresh sai do TokenRefreshAuthenticator, por fora da ChamadosApi.
        val appRoutes = (annotated + "POST /auth/refresh").map(::anyParameter).toSet()
        val succeeded =
            recordings.values
                .filter {
                    it.response
                        .getValue("status")
                        .jsonPrimitive.int < 300
                }.map { anyParameter("${it.request.text("method")} ${it.request.text("path")}") }
                .toSet()

        assertEquals(emptySet<String>(), appRoutes - succeeded)
    }

    @Test
    fun `cada resposta gravada segue o contrato tambem pelo validador do app`() {
        val failures =
            recordings.flatMap { (name, recorded) ->
                val status =
                    recorded.response
                        .getValue("status")
                        .jsonPrimitive.int
                val path = recorded.request.text("url").substringBefore('?')
                val schema = OpenApiContract.responseSchema(recorded.request.text("method"), path, status)
                val body = recorded.response["body"]
                when {
                    schema != null && body != null -> SchemaValidator.errors(schema, body).map { "$name: $it" }
                    schema != body -> listOf("$name: corpo ${if (body == null) "ausente" else "não declarado"}")
                    else -> emptyList()
                }
            }

        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `o app manda o que o backend recebeu e le o que ele respondeu`() =
        runTest {
            val failures = cases.flatMap { (name, case) -> replay(name, case) }

            assertEquals(emptyList<String>(), failures)
        }

    // Um caso: servidor e pilha novos, a resposta gravada servida, o caso refeito, e a requisição e o resultado conferidos

    private class Case(
        /** Entra antes como o admin, pelo `auth-login` gravado, na secretaria de Saúde, e esquece as requisições do login. */
        val signedIn: Boolean = false,
        /** Falso quando o roteiro do backend manda o que o app não manda: a requisição não é comparada. */
        val sends: Boolean = true,
        /** Casos servidos antes do próprio, na ordem em que o app os pede, nas rotas deles. */
        val serve: List<String> = emptyList(),
        /** O resultado esperado. Sem ele, sucesso em 2xx, e o [DomainError] do status em erro. */
        val expected: (Recorded.() -> AppResult<*>)? = null,
        val replay: suspend Replay.() -> AppResult<*>,
    )

    private class Recorded(
        val json: JsonObject,
    ) {
        val request = json.getValue("request").jsonObject
        val response = json.getValue("response").jsonObject
        val route get() = "${request.text("method")} /api${request.text("url").substringBefore('?')}"
    }

    private val recordings: Map<String, Recorded> by lazy {
        val snapshot = File(System.getProperty("chamados.responses") ?: "contracts/backend/responses.json")
        assertTrue("respostas gravadas não encontradas em ${snapshot.absoluteFile}: rode pelo Gradle", snapshot.isFile)
        Json.parseToJsonElement(snapshot.readText()).jsonObject.mapValues { (_, value) -> Recorded(value.jsonObject) }
    }

    private suspend fun TestScope.replay(
        name: String,
        case: Case,
    ): List<String> {
        val recorded = recordings[name] ?: return listOf("$name: sem arquivo")
        val server = Server()
        try {
            val replay = Replay(recorded, server, newStack(server), UnconfinedTestDispatcher(testScheduler))
            if (case.signedIn) replay.signIn()
            server.serve(*case.serve.map(recordings::getValue).toTypedArray())
            server.serve(recorded)
            val result =
                runCatching { case.replay(replay) }
                    .getOrElse { AppResult.Failure(DomainError.Unknown("${it::class.simpleName}: ${it.message}")) }
            val expected = case.expected?.invoke(recorded) ?: defaultExpectation(recorded)
            return buildList {
                if (!matches(expected, result)) add("$name: esperava ${expected ?: "sucesso"}, veio $result")
                if (case.sends) addAll(requestDifferences(recorded, server.requests).map { "$name: $it" })
            }
        } finally {
            server.close()
        }
    }

    private fun defaultExpectation(recorded: Recorded): AppResult<*>? {
        val status =
            recorded.response
                .getValue("status")
                .jsonPrimitive.int
        if (status < 300) return null
        val body = recorded.response["body"]?.jsonObject
        val message =
            body
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        val error =
            when (status) {
                400, 409, 422 -> {
                    val violations =
                        body
                            ?.get("violations")
                            ?.jsonObject
                            .orEmpty()
                            .mapValues { it.value.jsonPrimitive.content }
                    DomainError.Validation(message, violations)
                }

                401 -> {
                    DomainError.Unauthorized()
                }

                403 -> {
                    DomainError.Forbidden(message)
                }

                404 -> {
                    DomainError.NotFound(message)
                }

                else -> {
                    DomainError.Unknown(message)
                }
            }
        return AppResult.Failure(error)
    }

    /** Sem expectativa, vale sucesso com qualquer valor. */
    private fun matches(
        expected: AppResult<*>?,
        actual: AppResult<*>,
    ) = if (expected == null) actual is AppResult.Success else expected == actual

    private fun requestDifferences(
        recorded: Recorded,
        requests: List<RecordedRequest>,
    ): List<String> {
        val sent = requests.filter { "${it.method} ${it.url.encodedPath}" == recorded.route }
        if (sent.size != 1) return listOf("esperava uma requisição ${recorded.route}, vieram ${sent.size}: ${requests.map { it.url }}")
        val request = sent.single()
        val url =
            request.url.encodedPath +
                request.url.encodedQuery
                    ?.let { "?$it" }
                    .orEmpty()
        val expectedBody = recorded.request["body"]
        val expectedParts = recorded.request["multipart"]
        val body = request.body?.utf8().orEmpty()
        return buildList {
            if (url != "/api" + recorded.request.text("url")) add("url $url no lugar de /api${recorded.request.text("url")}")
            when {
                expectedBody != null -> {
                    val parsed = runCatching { Json.parseToJsonElement(body) }.getOrNull()
                    if (parsed != expectedBody) add("corpo $body no lugar de $expectedBody")
                }

                expectedParts != null -> {
                    val parts = parts(request)
                    if (parts != expectedParts) add("partes $parts no lugar de $expectedParts")
                }

                body.isNotEmpty() -> {
                    add("corpo $body onde o backend não recebeu nenhum")
                }
            }
        }
    }

    /** Nome, arquivo e tamanho de cada parte, como o backend grava. */
    private fun parts(request: RecordedRequest): JsonArray {
        val boundary = request.headers["Content-Type"]?.toMediaType()?.parameter("boundary") ?: return JsonArray(emptyList())
        return MultipartReader(Buffer().write(requireNotNull(request.body)), boundary).use { reader ->
            JsonArray(
                generateSequence { reader.nextPart() }
                    .map { part ->
                        val disposition = part.headers["Content-Disposition"].orEmpty()
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive(disposition.substringAfter("name=\"").substringBefore('"')),
                                "filename" to JsonPrimitive(disposition.substringAfter("filename=\"").substringBefore('"')),
                                "size" to JsonPrimitive(part.body.readByteString().size),
                            ),
                        )
                    }.toList(),
            )
        }
    }

    // O que um caso usa para se refazer

    private inner class Replay(
        val recorded: Recorded,
        val server: Server,
        stack: Stack,
        io: CoroutineDispatcher,
    ) {
        val api = stack.api
        val session = stack.session
        val auth = RemoteAuthRepository(stack.api, stack.session, stack.cookieJar, MemorySettings())
        val calls = RemoteCallRepository(stack.api, context, io)
        val catalog = RemoteCatalogRepository(stack.api)
        val applicants = RemoteApplicantRepository(stack.api)
        val stock = RemoteStockRepository(stack.api)
        val body get() = recorded.request.getValue("body").jsonObject

        /** O segmento [index] do caminho gravado, sem o `/api`: `/calls/{id}` tem o id no 1. */
        fun segment(index: Int) =
            recorded.request
                .text("url")
                .substringBefore('?')
                .removePrefix("/")
                .split('/')[index]

        fun query(name: String) = requireNotNull(("http://backend" + recorded.request.text("url")).toHttpUrl().queryParameter(name))

        /** O admin do seed tem duas secretarias: o login para na escolha. */
        fun signedInRole(): AppResult<Role> =
            (session.state.value as? SessionState.ChoosingSecretary)?.let { AppResult.Success(it.user.role) }
                ?: AppResult.Failure(DomainError.Unknown("sessão ${session.state.value}"))

        /** Entra como o admin e escolhe a secretaria de Saúde, como o roteiro do backend. */
        suspend fun signIn() {
            server.serve(recordings.getValue("auth-login"), recordings.getValue("auth-me"))
            auth.login(ADMIN, PASSWORD)
            val user = (session.state.value as SessionState.ChoosingSecretary).user
            auth.selectSecretary(user.secretaries.single { it.id == HEALTH })
            server.requests.clear()
        }

        fun newApplicant() =
            NewApplicant(
                name = body.text("name"),
                email = body.textOrNull("email"),
                phone = body.textOrNull("phone"),
                localId = body.text("localId"),
                positionId = body.text("positionId"),
            )

        fun newCall(of: String? = null): NewCall {
            val call =
                of?.let {
                    recordings
                        .getValue(it)
                        .request
                        .getValue("body")
                        .jsonObject
                } ?: body
            return NewCall(
                description = call.text("description"),
                status = CallStatus.valueOf(call.text("status")),
                priority = CallPriority.valueOf(call.text("priority")),
                applicantId = call.text("applicantId"),
                unitId = call.text("unitId"),
                localId = call.text("localId"),
                equipmentId = call.textOrNull("equipmentId"),
                categoryId = call.text("categoryId"),
                subcategoryId = call.text("subcategoryId"),
                technicianId = call.textOrNull("responsibleTechnicianId"),
            )
        }

        fun progress() =
            ProgressRequest(
                note = body.text("note"),
                status = CallStatus.valueOf(body.text("status")),
                technicianId = body.textOrNull("responsibleTechnicianId"),
                stockItems =
                    body["stockItems"]?.jsonArray.orEmpty().map {
                        UsedStockItem(
                            it.jsonObject.text("itemId"),
                            it.jsonObject
                                .getValue("quantity")
                                .jsonPrimitive.int,
                        )
                    },
            )

        /** O arquivo gravado, com conteúdo do mesmo tamanho, ou [name] com [content]. */
        fun attachments(
            name: String? = null,
            content: String? = null,
        ): List<PendingAttachment> {
            val part =
                recorded.request["multipart"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject
            val fileName = name ?: requireNotNull(part).text("filename")
            val bytes = content?.toByteArray() ?: ByteArray(requireNotNull(part).getValue("size").jsonPrimitive.int) { 'a'.code.toByte() }
            val file = File(context.cacheDir, "anexo-${bytes.size}").apply { writeBytes(bytes) }
            return listOf(PendingAttachment(Uri.fromFile(file).toString(), fileName, "text/plain", bytes.size.toLong()))
        }
    }

    // Servidor e pilha HTTP

    private class Stack(
        val cookieJar: PersistentCookieJar,
        val session: RemoteSession,
        val api: ChamadosApi,
    )

    private fun newStack(server: Server): Stack {
        val baseUrl = server.url
        val cookieJar = PersistentCookieJar(context).apply { clear() }
        val session = RemoteSession()
        val refreshClient = NetworkModule.refreshClient(cookieJar)
        val client =
            NetworkModule.okHttp(
                cookieJar,
                CsrfInterceptor(refreshClient, baseUrl),
                SessionHeadersInterceptor(cookieJar, session),
                TokenRefreshAuthenticator(refreshClient, baseUrl, cookieJar, session),
            )
        return Stack(cookieJar, session, NetworkModule.api(client, baseUrl))
    }

    /** Devolve as respostas gravadas na rota de cada uma, e o `auth-csrf` a cada pedido do CSRF, como o backend. */
    private inner class Server : AutoCloseable {
        private val mockWebServer = MockWebServer()
        private val queues = ConcurrentHashMap<String, ConcurrentLinkedQueue<MockResponse>>()
        private val cookieValues = AtomicInteger()
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val url: String

        init {
            val csrf = recordings.getValue("auth-csrf")
            mockWebServer.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        requests += request
                        val route = "${request.method} ${request.url.encodedPath}"
                        if (route == csrf.route) return response(csrf)
                        return queues[route]?.poll() ?: MockResponse.Builder().code(UNEXPECTED).build()
                    }
                }
            mockWebServer.start()
            url = mockWebServer.url("/api/").toString()
        }

        fun serve(vararg recorded: Recorded) = recorded.forEach { queues.getOrPut(it.route) { ConcurrentLinkedQueue() }.add(response(it)) }

        /**
         * A resposta gravada, com os cookies dela. O valor "valor" vira um valor novo a cada cookie, e o Secure sai:
         * o servidor local é HTTP, e o OkHttp não manda cookie Secure fora do HTTPS.
         */
        private fun response(recorded: Recorded): MockResponse {
            val response = recorded.response
            return MockResponse
                .Builder()
                .code(response.getValue("status").jsonPrimitive.int)
                .apply {
                    response["cookies"]?.jsonArray?.forEach { cookie ->
                        val header = cookie.jsonPrimitive.content.replace("; Secure", "")
                        addHeader("Set-Cookie", header.replaceFirst("=valor", "=valor-${cookieValues.incrementAndGet()}"))
                    }
                    response["body"]?.let { body ->
                        addHeader("Content-Type", "application/json")
                        body(body.toString())
                    }
                }.build()
        }

        override fun close() = mockWebServer.close()
    }

    private class MemorySettings : SettingsRepository {
        private val last = MutableStateFlow<Map<String, String?>>(emptyMap())
        override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)

        override fun lastSecretaryId(userId: String): Flow<String?> = last.map { it[userId] }

        override suspend fun setThemeMode(mode: ThemeMode) = Unit

        override suspend fun setLastSecretaryId(
            userId: String,
            secretaryId: String?,
        ) {
            last.update { it + (userId to secretaryId) }
        }
    }

    private companion object {
        const val ADMIN = "admin@chamados.test"
        const val PASSWORD = "senha-de-teste"
        const val HEALTH = "5eed0002-0000-4000-8000-000000000001"

        /** Rota sem resposta servida: um pedido que o caso não esperava. */
        const val UNEXPECTED = 599

        fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content

        fun JsonObject.textOrNull(name: String) = (get(name) as? JsonPrimitive)?.contentOrNull

        /** `/calls/{id}` e `/calls/{callId}` são a mesma rota. */
        fun anyParameter(route: String) = route.replace(Regex("\\{[^}]*}"), "{}")
    }
}
