package br.com.portosoftware.chamados.data.remote

import android.app.Application
import android.net.Uri
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallAttachment
import br.com.portosoftware.chamados.core.model.CallDashboard
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallHistoryEntry
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Equipment
import br.com.portosoftware.chamados.core.model.Local
import br.com.portosoftware.chamados.core.model.NewApplicant
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.Page
import br.com.portosoftware.chamados.core.model.PendingAttachment
import br.com.portosoftware.chamados.core.model.Position
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.Role
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.SubCategory
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.core.model.ThemeMode
import br.com.portosoftware.chamados.core.model.UsedStockItem
import br.com.portosoftware.chamados.core.model.User
import br.com.portosoftware.chamados.data.remote.network.CsrfInterceptor
import br.com.portosoftware.chamados.data.remote.network.PersistentCookieJar
import br.com.portosoftware.chamados.data.remote.network.RemoteSession
import br.com.portosoftware.chamados.data.remote.network.SessionHeadersInterceptor
import br.com.portosoftware.chamados.data.remote.network.TokenRefreshAuthenticator
import br.com.portosoftware.chamados.di.NetworkModule
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartReader
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * O que cada método da [ChamadosApi] envia e o que chega ao domínio, pela pilha HTTP do app (cookies, CSRF antes
 * das mutações, cabeçalhos da sessão e renovação em 401) contra um servidor local.
 *
 * As respostas são as capturadas do backend (`api-responses`), pedaços delas, ou as de `api-fixtures`. Cada uma passa
 * pelo schema da rota e do status em `contracts/openapi.yaml` antes de ser servida ([respond]), inclusive a do CSRF,
 * que o despachante serve a cada pedido. Ficam fora as respostas sem corpo ([respondEmpty]) e o 404 sem corpo da rota
 * sem resposta cadastrada, que só sinaliza uma requisição inesperada. O teste não cita nenhum tipo de transporte:
 * trocar esses tipos não pode mudar nada do que ele confere.
 * Os corpos enviados são comparados como JSON, porque a ordem das chaves não faz parte do contrato.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ApiWireTest {
    private val server = MockWebServer()
    private val responses = ConcurrentHashMap<String, ConcurrentLinkedQueue<MockResponse>>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val csrfTokens = AtomicInteger()
    private val settings = MemorySettings()
    private val context by lazy { RuntimeEnvironment.getApplication() }

    @Before
    fun start() {
        val csrf = fixture("csrf")
        assertFollowsContract("GET /api/auth/csrf", csrf, 200)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val route = "${request.method} ${request.url.encodedPath}"
                    // Como o backend: cada GET /auth/csrf troca o cookie, e a mutação seguinte precisa do novo.
                    if (route == "GET /api/auth/csrf") {
                        return json(csrf, "Set-Cookie" to "XSRF-TOKEN=csrf-${csrfTokens.incrementAndGet()}; Path=/")
                    }
                    return responses[route]?.poll() ?: MockResponse.Builder().code(404).build()
                }
            }
        server.start()
    }

    @After
    fun stop() = server.close()

    // Pilha HTTP

    private class Stack(
        val cookieJar: PersistentCookieJar,
        val session: RemoteSession,
        val api: ChamadosApi,
    )

    private fun newStack(): Stack {
        val baseUrl = server.url("/api/").toString()
        val cookieJar = PersistentCookieJar(context)
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

    private val stack by lazy { newStack() }

    private fun auth(on: Stack = stack) = RemoteAuthRepository(on.api, on.session, on.cookieJar, settings)

    private fun TestScope.calls() = RemoteCallRepository(stack.api, context, UnconfinedTestDispatcher(testScheduler))

    private fun catalog() = RemoteCatalogRepository(stack.api)

    private fun applicants() = RemoteApplicantRepository(stack.api)

    private fun stock() = RemoteStockRepository(stack.api)

    /** Entra como o admin de `call-full`, que tem uma secretaria só, e esquece as requisições do login. */
    private suspend fun signedIn(): RemoteAuthRepository {
        val auth = signIn()
        requests.clear()
        return auth
    }

    private suspend fun signIn(): RemoteAuthRepository {
        respond(
            "POST /api/auth/login",
            captured("session").toString(),
            headers = arrayOf("Set-Cookie" to "access_token=acesso-1; Path=/", "Set-Cookie" to "refresh_token=renovacao-1; Path=/"),
        )
        respond("GET /api/auth/me", callFull.at("openedBy").toString())
        val auth = auth()
        assertEquals(AppResult.Success(Unit), auth.login("  admin@chamados.test ", "demo"))
        return auth
    }

    // Respostas

    private fun resource(path: String) = requireNotNull(javaClass.getResource(path)) { path }.readText()

    private fun captured(name: String): JsonObject = Json.parseToJsonElement(resource("/api-responses/$name.json")).jsonObject

    private fun fixture(name: String) = resource("/api-fixtures/$name.json")

    private val callFull by lazy { captured("call-full") }
    private val callMinimal by lazy { captured("call-minimal") }
    private val callsFilter by lazy { captured("calls-filter") }

    private fun JsonElement.at(vararg path: Any): JsonElement =
        path.fold(this) { element, key -> if (key is Int) element.jsonArray[key] else element.jsonObject.getValue(key as String) }

    private fun json(
        body: String,
        vararg headers: Pair<String, String>,
        code: Int = 200,
    ): MockResponse =
        MockResponse
            .Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
            .body(body)
            .build()

    /** Serve [body] em [route], depois de conferir que ele segue o schema da rota e do status no contrato. */
    private fun respond(
        route: String,
        body: String,
        code: Int = 200,
        headers: Array<Pair<String, String>> = emptyArray(),
    ) {
        assertFollowsContract(route, body, code)
        responses.getOrPut(route) { ConcurrentLinkedQueue() }.add(json(body, *headers, code = code))
    }

    private fun assertFollowsContract(
        route: String,
        body: String,
        code: Int,
    ) {
        val (method, path) = route.split(" ", limit = 2)
        val schema = OpenApiContract.responseSchema(method, path.removePrefix("/api"), code)
        val errors =
            when {
                schema != null -> SchemaValidator.errors(schema, parse(body))
                body.isNotBlank() -> listOf("o contrato não declara corpo")
                else -> emptyList()
            }
        assertEquals("$route $code fora do contrato", emptyList<String>(), errors)
    }

    /** Resposta sem corpo. O status não passa pelo contrato: o 204 das listas é tolerado de propósito. */
    private fun respondEmpty(
        route: String,
        code: Int,
    ) {
        responses.getOrPut(route) { ConcurrentLinkedQueue() }.add(MockResponse.Builder().code(code).build())
    }

    // Requisições

    private fun routes() = requests.map { "${it.method} ${it.url.encodedPath}" }

    private fun RecordedRequest.query() = (0 until url.querySize).map { url.queryParameterName(it) to url.queryParameterValue(it) }

    private fun RecordedRequest.json(): JsonElement = Json.parseToJsonElement(requireNotNull(body).utf8())

    private fun parse(text: String) = Json.parseToJsonElement(text)

    private fun only(route: String) = requests.single { "${it.method} ${it.url.encodedPath}" == route }

    private fun <T> AppResult<T>.value() = (this as AppResult.Success).value

    // Autenticação e sessão

    @Test
    fun `login busca o CSRF, envia as credenciais com o token e entra na unica secretaria`() =
        runTest {
            val auth = signIn()

            assertEquals(listOf("GET /api/auth/csrf", "POST /api/auth/login", "GET /api/auth/me"), routes())
            val login = requests[1]
            assertEquals(parse("""{"email":"admin@chamados.test","password":"demo"}"""), login.json())
            assertEquals("csrf-1", login.headers["X-XSRF-TOKEN"])
            assertEquals(SessionState.Active(admin, secretary), auth.session.value)
            assertEquals("renovacao-1", stack.cookieJar.valueOf("refresh_token"))
        }

    @Test
    fun `sessao salva volta pelo auth me num processo novo, com os cookies e a secretaria`() =
        runTest {
            signedIn()
            respond("GET /api/auth/me", callFull.at("openedBy").toString())
            val restarted = newStack()

            auth(restarted).restore()

            assertEquals(SessionState.Active(admin, secretary), restarted.session.state.value)
            val me = only("GET /api/auth/me")
            assertTrue(me.headers["Cookie"].orEmpty(), me.headers["Cookie"].orEmpty().contains("access_token=acesso-1"))
        }

    @Test
    fun `token vencido renova por fora da API e repete a requisicao com o CSRF novo`() =
        runTest {
            signedIn()
            respond("GET /api/dashboard/calls", fixture("validation-error"), code = 401)
            respond(
                "POST /api/auth/refresh",
                captured("session").toString(),
                headers = arrayOf("Set-Cookie" to "access_token=acesso-2; Path=/", "Set-Cookie" to "refresh_token=renovacao-2; Path=/"),
            )
            respond("GET /api/dashboard/calls", dashboardBody())

            val dashboard = calls().dashboard().value()

            assertEquals(expectedDashboard, dashboard)
            assertEquals(
                listOf("GET /api/dashboard/calls", "GET /api/auth/csrf", "POST /api/auth/refresh", "GET /api/dashboard/calls"),
                routes(),
            )
            val refresh = requests[2]
            assertEquals("csrf-2", refresh.headers["X-XSRF-TOKEN"])
            assertTrue(refresh.headers["Cookie"].orEmpty().contains("refresh_token=renovacao-1"))
            assertEquals(0L, refresh.bodySize)
            val retry = requests[3]
            assertEquals("csrf-2", retry.headers["X-XSRF-TOKEN"])
            assertTrue(retry.headers["Cookie"].orEmpty().contains("access_token=acesso-2"))
            assertEquals(secretary.id, retry.headers["X-Active-Secretary-Id"])
            assertEquals(SessionState.Active(admin, secretary), stack.session.state.value)
        }

    @Test
    fun `esqueci a senha manda o email com um CSRF novo`() =
        runTest {
            respond("POST /api/auth/forgot-password", """{"message":"Se o e-mail existir, enviaremos as instruções."}""")

            val result = auth().requestPasswordReset("  admin@chamados.test ")

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(listOf("GET /api/auth/csrf", "GET /api/auth/csrf", "POST /api/auth/forgot-password"), routes())
            val reset = requests[2]
            assertEquals(parse("""{"email":"admin@chamados.test"}"""), reset.json())
            assertEquals("csrf-2", reset.headers["X-XSRF-TOKEN"])
        }

    @Test
    fun `sair avisa o backend com CSRF e apaga os cookies`() =
        runTest {
            val auth = signedIn()
            respondEmpty("POST /api/auth/logout", 200)

            auth.logout()

            assertEquals(listOf("GET /api/auth/csrf", "POST /api/auth/logout"), routes())
            assertEquals("csrf-2", requests[1].headers["X-XSRF-TOKEN"])
            assertEquals(SessionState.SignedOut, auth.session.value)
            assertNull(stack.cookieJar.valueOf("refresh_token"))
            assertNull(stack.cookieJar.valueOf("XSRF-TOKEN"))
        }

    // Chamados

    private fun dashboardBody() =
        JsonObject(
            mapOf(
                "totalActive" to JsonPrimitive(4),
                "newCalls" to JsonPrimitive(1),
                "inProgress" to JsonPrimitive(2),
                "urgent" to JsonPrimitive(1),
                "unassigned" to JsonPrimitive(3),
                "recentCalls" to callsFilter.at("content"),
            ),
        ).toString()

    @Test
    fun `painel le os contadores e os recentes com a secretaria ativa`() =
        runTest {
            signedIn()
            respond("GET /api/dashboard/calls", dashboardBody())

            assertEquals(expectedDashboard, calls().dashboard().value())
            assertEquals(secretary.id, only("GET /api/dashboard/calls").headers["X-Active-Secretary-Id"])
        }

    @Test
    fun `filtro manda cada parametro na ordem e le a pagina`() =
        runTest {
            signedIn()
            respond("GET /api/calls/filter", callsFilter.toString())
            val filter =
                CallFilter(
                    search = "  monitor ",
                    statuses = linkedSetOf(CallStatus.OPENED, CallStatus.IN_PROGRESS),
                    priority = CallPriority.URGENT,
                    unassigned = true,
                    categoryId = CATEGORY_ID,
                    technicianId = TECHNICIAN_ID,
                    unitId = UNIT_ID,
                )

            val page = calls().search(filter, 1, 20).value()

            assertEquals(Page(listOf(summaryInProgress, summaryWithoutStatus), 0, 2, 4L), page)
            assertEquals(
                listOf(
                    "search" to "monitor",
                    "status" to "OPENED",
                    "status" to "IN_PROGRESS",
                    "priority" to "URGENT",
                    "categoryId" to CATEGORY_ID,
                    "responsibleTechnicianId" to TECHNICIAN_ID,
                    "unitId" to UNIT_ID,
                    "unassigned" to "true",
                    "page" to "1",
                    "size" to "20",
                ),
                only("GET /api/calls/filter").query(),
            )
        }

    @Test
    fun `filtro vazio manda so a pagina`() =
        runTest {
            signedIn()
            respond("GET /api/calls/filter", callsFilter.toString())

            calls().search(CallFilter(), 0, 20).value()

            assertEquals(listOf("page" to "0", "size" to "20"), only("GET /api/calls/filter").query())
        }

    @Test
    fun `detalhe completo chega inteiro ao dominio`() =
        runTest {
            signedIn()
            respond("GET /api/calls/$CALL_FULL_ID", callFull.toString())

            assertEquals(expectedFullDetail, calls().detail(CALL_FULL_ID).value())
        }

    @Test
    fun `detalhe minimo, sem status e sem relacoes, chega ao dominio`() =
        runTest {
            signedIn()
            respond("GET /api/calls/$CALL_MINIMAL_ID", callMinimal.toString())

            assertEquals(expectedMinimalDetail, calls().detail(CALL_MINIMAL_ID).value())
        }

    @Test
    fun `novo chamado manda todos os campos com CSRF novo e le o 201`() =
        runTest {
            signedIn()
            respond("POST /api/calls", callMinimal.toString(), code = 201)

            val created = calls().create(newCall).value()

            assertEquals(expectedMinimalDetail, created)
            assertEquals(listOf("GET /api/auth/csrf", "POST /api/calls"), routes())
            val post = requests[1]
            assertEquals("csrf-2", post.headers["X-XSRF-TOKEN"])
            assertEquals(
                parse(
                    """
                    {"description":"Monitor piscando desde a manhã.","status":"OPENED","priority":"HIGH",
                    "equipmentId":"$EQUIPMENT_ID","subcategoryId":"$SUBCATEGORY_ID","categoryId":"$CATEGORY_ID",
                    "applicantId":"$APPLICANT_ID","localId":"$LOCAL_ID","unitId":"$UNIT_ID","responsibleTechnicianId":"$TECHNICIAN_ID"}
                    """.trimIndent(),
                ),
                post.json(),
            )
        }

    @Test
    fun `novo chamado sem equipamento nem responsavel nao manda as chaves`() =
        runTest {
            signedIn()
            respond("POST /api/calls", callMinimal.toString(), code = 201)

            calls().create(newCall.copy(equipmentId = null, technicianId = null)).value()

            assertEquals(
                setOf("description", "status", "priority", "subcategoryId", "categoryId", "applicantId", "localId", "unitId"),
                only("POST /api/calls").json().jsonObject.keys,
            )
        }

    @Test
    fun `recusa do backend chega com a mensagem e os erros de campo`() =
        runTest {
            signedIn()
            respond("POST /api/calls", fixture("validation-error"), code = 400)

            val result = calls().create(newCall)

            assertEquals(
                AppResult.Failure(
                    DomainError.Validation(
                        "O conteúdo da requisição é inválido.",
                        mapOf("description" to "A descrição é obrigatória.", "localId" to "O local é obrigatório."),
                    ),
                ),
                result,
            )
        }

    @Test
    fun `atendimento com pecas manda nota, status, responsavel e itens`() =
        runTest {
            signedIn()
            respond("PATCH /api/calls/$CALL_FULL_ID/progress", callFull.toString())
            val request =
                ProgressRequest(
                    note = "  Trocada a fonte. ",
                    status = CallStatus.IN_PROGRESS,
                    technicianId = TECHNICIAN_ID,
                    stockItems = listOf(UsedStockItem(ITEM_ID, 1)),
                )

            val detail = calls().registerProgress(CALL_FULL_ID, request).value()

            assertEquals(expectedFullDetail, detail)
            assertEquals(listOf("GET /api/auth/csrf", "PATCH /api/calls/$CALL_FULL_ID/progress"), routes())
            assertEquals("csrf-2", requests[1].headers["X-XSRF-TOKEN"])
            assertEquals(
                parse(
                    """
                    {"note":"Trocada a fonte.","status":"IN_PROGRESS","responsibleTechnicianId":"$TECHNICIAN_ID",
                    "stockItems":[{"itemId":"$ITEM_ID","quantity":1}]}
                    """.trimIndent(),
                ),
                requests[1].json(),
            )
        }

    @Test
    fun `atendimento sem pecas nem responsavel manda so nota e status`() =
        runTest {
            signedIn()
            respond("PATCH /api/calls/$CALL_FULL_ID/progress", callFull.toString())

            calls().registerProgress(CALL_FULL_ID, ProgressRequest("Encerrado.", CallStatus.CLOSED, null, emptyList())).value()

            assertEquals(
                parse("""{"note":"Encerrado.","status":"CLOSED"}"""),
                only("PATCH /api/calls/$CALL_FULL_ID/progress").json(),
            )
        }

    @Test
    fun `anexos vao em multipart com o nome files e voltam como lista`() =
        runTest {
            signedIn()
            respond("POST /api/calls/$CALL_FULL_ID/attachments", callFull.at("attachments").toString())
            val photo = File(context.cacheDir, "foto.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val note = File(context.cacheDir, "relato.txt").apply { writeText("texto") }
            val files =
                listOf(
                    PendingAttachment(Uri.fromFile(photo).toString(), "foto.jpg", "image/jpeg", 3),
                    PendingAttachment(Uri.fromFile(note).toString(), "relato.txt", null, 5),
                )

            val attachments = calls().uploadAttachments(CALL_FULL_ID, files).value()

            assertEquals(expectedAttachments, attachments)
            assertEquals(listOf("GET /api/auth/csrf", "POST /api/calls/$CALL_FULL_ID/attachments"), routes())
            val upload = requests[1]
            assertEquals("csrf-2", upload.headers["X-XSRF-TOKEN"])
            val boundary = requireNotNull(upload.headers["Content-Type"]).toMediaType().parameter("boundary")!!
            val parts =
                MultipartReader(Buffer().write(requireNotNull(upload.body)), boundary).use { reader ->
                    generateSequence { reader.nextPart() }
                        .map { Triple(it.headers["Content-Disposition"], it.headers["Content-Type"], it.body.readByteString().utf8()) }
                        .toList()
                }
            assertEquals(
                listOf(
                    Triple("form-data; name=\"files\"; filename=\"foto.jpg\"", "image/jpeg", String(byteArrayOf(1, 2, 3))),
                    Triple("form-data; name=\"files\"; filename=\"relato.txt\"", null, "texto"),
                ),
                parts,
            )
        }

    /**
     * O backend lê o nome do arquivo do `Content-Disposition` em UTF-8 (o Spring Boot força a codificação da requisição)
     * e recusa acima de 10 MB com 413, no schema de erro.
     */
    @Test
    fun `anexo com acento no nome vai em UTF-8, e o 413 do backend chega com a mensagem`() =
        runTest {
            signedIn()
            respond("POST /api/calls/$CALL_FULL_ID/attachments", callFull.at("attachments").toString())
            val report = File(context.cacheDir, "relatorio.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val files = listOf(PendingAttachment(Uri.fromFile(report).toString(), "relatório de visita.pdf", "application/pdf", 3))

            calls().uploadAttachments(CALL_FULL_ID, files).value()

            val body = requireNotNull(only("POST /api/calls/$CALL_FULL_ID/attachments").body).utf8()
            assertTrue(body, body.contains("Content-Disposition: form-data; name=\"files\"; filename=\"relatório de visita.pdf\""))
            assertTrue(body, body.contains("Content-Type: application/pdf"))

            val tooLarge =
                """{"timestamp":"2026-11-07T20:12:31.886942735Z","status":413,"error":"Content Too Large",
                |"message":"O conteúdo enviado excede o limite permitido.","path":"/api/calls/$CALL_FULL_ID/attachments","violations":{}}
                """.trimMargin()
            respond("POST /api/calls/$CALL_FULL_ID/attachments", tooLarge, code = 413)

            assertEquals(
                AppResult.Failure(DomainError.Unknown("O conteúdo enviado excede o limite permitido.")),
                calls().uploadAttachments(CALL_FULL_ID, files),
            )
        }

    /**
     * O provedor pode não informar o tamanho, e a escolha fica com 0. O envio conta os bytes e recusa, sem abrir a
     * conexão, o arquivo acima de 10 MB e o envio acima de 25 MB, com o texto da escolha. Sem isso, o arquivo inteiro
     * subia e voltava como o 413 genérico do backend, ou, acima de 25 MB, como o 413 em HTML do nginx.
     */
    @Test
    fun `anexo de tamanho desconhecido acima do limite e recusado sem requisicao`() =
        runTest {
            signedIn()

            assertEquals(
                AppResult.Failure(DomainError.Validation("video.pdf passa de 10 MB.")),
                calls().uploadAttachments(CALL_FULL_ID, listOf(unknownSize("video.pdf", 10 * MB + 1))),
            )
            assertEquals(
                AppResult.Failure(DomainError.Validation("Os anexos passam do limite de 25 MB por envio.")),
                calls().uploadAttachments(
                    CALL_FULL_ID,
                    listOf(unknownSize("a.pdf", 9 * MB), unknownSize("b.pdf", 9 * MB), unknownSize("c.pdf", 8 * MB)),
                ),
            )
            assertEquals(emptyList<String>(), routes())
        }

    @Test
    fun `anexo de tamanho desconhecido no limite vai inteiro`() =
        runTest {
            signedIn()
            respond("POST /api/calls/$CALL_FULL_ID/attachments", callFull.at("attachments").toString())

            calls().uploadAttachments(CALL_FULL_ID, listOf(unknownSize("limite.pdf", 10 * MB))).value()

            val upload = only("POST /api/calls/$CALL_FULL_ID/attachments")
            val boundary = requireNotNull(upload.headers["Content-Type"]).toMediaType().parameter("boundary")!!
            val sizes =
                MultipartReader(Buffer().write(requireNotNull(upload.body)), boundary).use { reader ->
                    generateSequence { reader.nextPart() }.map { it.body.readByteString().size }.toList()
                }
            assertEquals(listOf(10 * MB), sizes)
        }

    /** Um arquivo de [size] bytes que a escolha registrou sem tamanho. */
    private fun unknownSize(
        name: String,
        size: Int,
    ): PendingAttachment {
        val file = File(context.cacheDir, name).apply { writeBytes(ByteArray(size)) }
        return PendingAttachment(Uri.fromFile(file).toString(), name, "application/pdf", 0)
    }

    // Catálogos, solicitantes e estoque

    @Test
    fun `unidades e locais da secretaria e da unidade`() =
        runTest {
            signedIn()
            respond("GET /api/unit/secretary/${secretary.id}", JsonArray(listOf(callFull.at("unit"))).toString())
            respond("GET /api/local/unit/$UNIT_ID", JsonArray(listOf(callFull.at("local"))).toString())

            assertEquals(listOf(OrgUnit(UNIT_ID, "Unidade")), catalog().units(secretary.id).value())
            assertEquals(listOf(Local(LOCAL_ID, "Local", UNIT_ID)), catalog().locals(UNIT_ID).value())
        }

    @Test
    fun `equipamentos do local, com e sem modelo, e 204 como lista vazia`() =
        runTest {
            signedIn()
            val withoutModel =
                JsonObject(
                    callFull.at("equipment").jsonObject +
                        mapOf("id" to JsonPrimitive(EQUIPMENT_WITHOUT_MODEL_ID), "patrimony" to JsonNull, "model" to JsonNull),
                )
            respond("GET /api/equipment/local/$LOCAL_ID", JsonArray(listOf(callFull.at("equipment"), withoutModel)).toString())
            respondEmpty("GET /api/equipment/local/$LOCAL_ID", 204)

            assertEquals(
                listOf(expectedEquipment, Equipment(EQUIPMENT_WITHOUT_MODEL_ID, null, null)),
                catalog().equipment(LOCAL_ID).value(),
            )
            assertEquals(emptyList<Equipment>(), catalog().equipment(LOCAL_ID).value())
        }

    @Test
    fun `categorias pedem 200 por pagina e leem os totais`() =
        runTest {
            signedIn()
            val page =
                JsonObject(
                    mapOf(
                        "content" to JsonArray(listOf(callFull.at("subcategory", "category"))),
                        "page" to Json.parseToJsonElement("""{"size":200,"number":0,"totalElements":201,"totalPages":2}"""),
                    ),
                )
            respond("GET /api/category/all", page.toString())

            assertEquals(Page(listOf(Category(CATEGORY_ID, "Categoria")), 0, 2, 201L), catalog().categories().value())
            assertEquals(listOf("size" to "200"), only("GET /api/category/all").query())
        }

    @Test
    fun `subcategorias da categoria, e lista vazia`() =
        runTest {
            signedIn()
            respond("GET /api/sub-category/category/$CATEGORY_ID", JsonArray(listOf(callFull.at("subcategory"))).toString())
            respond("GET /api/sub-category/category/$CATEGORY_ID", "[]")

            assertEquals(listOf(SubCategory(SUBCATEGORY_ID, "Sub", CATEGORY_ID)), catalog().subcategories(CATEGORY_ID).value())
            assertEquals(emptyList<SubCategory>(), catalog().subcategories(CATEGORY_ID).value())
        }

    @Test
    fun `tecnicos, inclusive o que o banco deixou sem nome`() =
        runTest {
            signedIn()
            val technicians = JsonArray(listOf(callFull.at("responsibleTechnician"), callFull.at("attachments", 1, "uploadedBy")))
            respond("GET /api/users/technicians", technicians.toString())

            assertEquals(
                listOf(Technician(TECHNICIAN_ID, "Tech"), Technician(UNNAMED_USER_ID, "Sem nome")),
                catalog().technicians().value(),
            )
        }

    @Test
    fun `cargos ativos e cadastro de cargo`() =
        runTest {
            signedIn()
            val position = callFull.at("applicant", "position")
            respond("GET /api/positions/active", JsonArray(listOf(position)).toString())
            respond("POST /api/positions", position.toString(), code = 201)

            assertEquals(listOf(Position(POSITION_ID, "Cargo")), catalog().positions().value())
            assertEquals(Position(POSITION_ID, "Cargo"), catalog().createPosition("  Cargo ").value())
            assertEquals(parse("""{"name":"Cargo"}"""), only("POST /api/positions").json())
            assertEquals("csrf-2", only("POST /api/positions").headers["X-XSRF-TOKEN"])
        }

    @Test
    fun `busca de solicitantes manda q e le completo e minimo`() =
        runTest {
            signedIn()
            val found = JsonArray(listOf(callFull.at("applicant"), callMinimal.at("applicant")))
            respond("GET /api/applicants/search", found.toString())

            assertEquals(listOf(fullApplicant, minimalApplicant), applicants().search("  Solic ").value())
            assertEquals(listOf("q" to "Solic"), only("GET /api/applicants/search").query())
        }

    @Test
    fun `cadastro de solicitante nao manda telefone em branco`() =
        runTest {
            signedIn()
            respond("POST /api/applicants", callFull.at("applicant").toString(), code = 201)
            val applicant = NewApplicant(" Solicitante ", " s@chamados.test ", "   ", LOCAL_ID, POSITION_ID)

            assertEquals(fullApplicant, applicants().create(applicant).value())
            assertEquals(
                parse("""{"name":"Solicitante","email":"s@chamados.test","localId":"$LOCAL_ID","positionId":"$POSITION_ID"}"""),
                only("POST /api/applicants").json(),
            )
            assertEquals("csrf-2", only("POST /api/applicants").headers["X-XSRF-TOKEN"])
        }

    @Test
    fun `estoque pede itens em estoque e le o item completo e o sem modelo`() =
        runTest {
            signedIn()
            respond("GET /api/items/all", fixture("items"))

            val page = stock().items("  fonte ").value()

            assertEquals(Page(listOf(fullStockItem, bareStockItem), 0, 1, 2L), page)
            assertEquals(listOf("search" to "fonte", "status" to "STOCK", "size" to "200"), only("GET /api/items/all").query())
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
        const val MB = 1024 * 1024
        const val CALL_FULL_ID = "1607ab39-e671-5ee5-bc94-bde22d2ab011"
        const val CALL_MINIMAL_ID = "6ee02893-ea48-5518-9e9e-70ba899d3565"
        const val UNIT_ID = "ccc32ac7-6042-5b2e-8f64-bc3b3b4e6d19"
        const val LOCAL_ID = "a1dc742c-68fa-514b-a5a0-e5b1ceb849ef"
        const val CATEGORY_ID = "533e1916-b979-5379-8c39-1e6868857446"
        const val SUBCATEGORY_ID = "41f7ca5d-8ec9-56ea-8539-04a89879eb60"
        const val APPLICANT_ID = "48b30255-8570-50f0-8624-369ec2fb397b"
        const val POSITION_ID = "997da223-428d-53d0-ab46-14219533e970"
        const val EQUIPMENT_ID = "0593c413-5ef0-58c8-ad85-d021fc11b11c"
        const val EQUIPMENT_WITHOUT_MODEL_ID = "3f9b2c71-8d4e-4a65-b1c0-7e2f9a4d8b16"
        const val TECHNICIAN_ID = "52c19625-6830-5d03-ad2e-50bb7ffbfe1a"
        const val UNNAMED_USER_ID = "a3952cc8-509d-5ebd-b21a-ffe36f0a77b3"
        const val ITEM_ID = "5d0f1c2a-7b3e-4f61-9a24-0c8e1b7d3a51"

        /** Os dois resumos de `calls-filter`: prioridade normal, do solicitante "Mínimo", sem local, categoria nem técnico. */
        fun summary(
            id: String,
            description: String,
            status: CallStatus,
            openedAt: LocalDateTime,
        ) = CallSummary(
            id = id,
            description = description,
            status = status,
            priority = CallPriority.NORMAL,
            openedAt = openedAt,
            applicantName = "Mínimo",
            unitName = "Unidade",
            localName = null,
            categoryName = null,
            subcategoryName = null,
            technicianName = null,
            equipmentPatrimony = null,
        )

        val secretary = Secretary("4219e511-6c89-5965-8a4d-60889c541779", "Sec Revisão")
        val admin = User("734eb096-7ee3-567c-bae3-65518a6a6448", "Admin", "admin@chamados.test", Role.ADMIN, listOf(secretary))

        val summaryInProgress =
            summary("17cfcdb9-3068-5f5e-b055-fdfc8fd88a34", "Técnico sem nome", CallStatus.IN_PROGRESS, LocalDateTime.of(2026, 3, 2, 5, 0))
        val summaryWithoutStatus = summary(CALL_MINIMAL_ID, "Mínimo", CallStatus.UNKNOWN, LocalDateTime.of(2026, 3, 1, 5, 0))
        val expectedDashboard = CallDashboard(4, 1, 2, 1, 3, listOf(summaryInProgress, summaryWithoutStatus))

        val fullApplicant =
            Applicant(APPLICANT_ID, "Solicitante", "s@chamados.test", "1", UNIT_ID, "Unidade", LOCAL_ID, "Local", POSITION_ID, "Cargo")
        val minimalApplicant =
            Applicant("93f1b18a-4077-58e7-a93e-5c1d0a9a5622", "Mínimo", null, null, UNIT_ID, "Unidade", LOCAL_ID, "Local", null, null)

        val expectedEquipment = Equipment(EQUIPMENT_ID, "PAT", "Tipo Eq Marca Modelo Eq")

        val expectedAttachments =
            listOf(
                CallAttachment("c73a878e-36f0-548e-9a48-ac4367d49362", "a.txt", "text/plain", 1),
                CallAttachment("d2841db5-ca5a-5e58-b770-6e34bbb31245", "b.txt", null, 1),
            )

        val expectedFullDetail =
            CallDetail(
                id = CALL_FULL_ID,
                description = "Completo",
                status = CallStatus.OPENED,
                priority = CallPriority.NORMAL,
                openedAt = LocalDateTime.of(2026, 2, 1, 5, 0),
                completedAt = LocalDateTime.of(2026, 1, 1, 5, 0),
                applicant = fullApplicant,
                unitName = "Unidade",
                localName = "Local",
                categoryName = "Categoria",
                subcategoryName = "Sub",
                equipment = expectedEquipment,
                technician = Technician(TECHNICIAN_ID, "Tech"),
                openedByName = "Admin",
                history =
                    listOf(
                        CallHistoryEntry("a41611d1-1f63-5a42-9651-fe9dd791f33a", LocalDateTime.of(2026, 1, 1, 5, 0), "Evento", "Admin"),
                        CallHistoryEntry("473e539f-9eda-54bc-9c32-b8d8db9d1b82", LocalDateTime.of(2026, 1, 1, 5, 0), "Evento min", null),
                    ),
                attachments = expectedAttachments,
            )

        val expectedMinimalDetail =
            CallDetail(
                id = CALL_MINIMAL_ID,
                description = "Mínimo",
                status = CallStatus.UNKNOWN,
                priority = CallPriority.NORMAL,
                openedAt = LocalDateTime.of(2026, 3, 1, 5, 0),
                completedAt = null,
                applicant = minimalApplicant,
                unitName = "Unidade",
                localName = null,
                categoryName = null,
                subcategoryName = null,
                equipment = null,
                technician = null,
                openedByName = null,
                history = emptyList(),
                attachments = emptyList(),
            )

        val newCall =
            NewCall(
                description = "  Monitor piscando desde a manhã. ",
                status = CallStatus.OPENED,
                priority = CallPriority.HIGH,
                applicantId = APPLICANT_ID,
                unitId = UNIT_ID,
                localId = LOCAL_ID,
                equipmentId = EQUIPMENT_ID,
                categoryId = CATEGORY_ID,
                subcategoryId = SUBCATEGORY_ID,
                technicianId = TECHNICIAN_ID,
            )

        val fullStockItem =
            StockItem(
                id = ITEM_ID,
                model = "Fonte 500W",
                brand = "Marca",
                type = "Fonte",
                quantity = 3,
                consumable = false,
                patrimony = null,
                serialNumber = "SN-500",
                specifications = listOf("Potência" to "500 W"),
                isUnique = true,
            )
        val bareStockItem = StockItem("9ea0cc89-304b-564c-83a1-2cedaf70ccad", "Item", null, null, 0, false, null, null, emptyList(), false)
    }
}
