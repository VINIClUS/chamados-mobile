package br.com.portosoftware.chamados.data.remote

import android.app.Application
import br.com.portosoftware.chamados.contract.model.CallResponseDTO
import br.com.portosoftware.chamados.contract.model.CallSummaryResponseDTO
import br.com.portosoftware.chamados.contract.model.UserResponseDTO
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDateTime

/**
 * O que o app envia e lê tem de bater com os records e os parâmetros do backend. Os corpos de resposta seguem o
 * contrato: os modelos gerados recusam chave obrigatória ausente, então JSON recortado não serve mais aqui.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ContractTest {
    private var lastRequest: Request? = null

    private fun api(body: String): ChamadosApi =
        Retrofit
            .Builder()
            .baseUrl("https://chamados.test/")
            .client(
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        lastRequest = chain.request()
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build(),
            ).addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ChamadosApi::class.java)

    private fun calls(body: String) = RemoteCallRepository(api(body), RuntimeEnvironment.getApplication(), UnconfinedTestDispatcher())

    private val emptyPage = """{"content":[],"page":{"size":20,"number":0,"totalElements":0,"totalPages":0}}"""

    private fun <T> AppResult<T>.value() = (this as AppResult.Success).value

    private fun resource(path: String) = requireNotNull(javaClass.getResource(path)).readText()

    private fun captured(name: String): JsonObject = ApiJson.parseToJsonElement(resource("/api-responses/$name.json")).jsonObject

    /** `call-minimal` capturado, com as chaves trocadas. */
    private fun minimalCall(vararg changes: Pair<String, JsonElement>) = JsonObject(captured("call-minimal") + changes).toString()

    /** Usuário capturado sem nome e sem papel, como o banco aceita. */
    private val unnamedUser by lazy { captured("call-minimal").getValue("applicant").jsonObject.getValue("createdBy") }

    private fun user(role: String) = """{"id":"u","name":"U","email":"u@x","role":$role,"enabled":true,"secretaries":[],"itTeams":[]}"""

    private fun summary(
        status: String,
        priority: String = "\"NORMAL\"",
    ) = """{"id":"c","description":"d","status":$status,"priority":$priority,"openingDate":"2026-11-06T22:48:47.095305",
        |"completionDate":null,"applicantName":"Solicitante","unitName":"Unidade","localName":null,"categoryName":null,
        |"subcategoryName":null,"responsibleTechnicianName":null,"equipmentPatrimony":null,"equipmentModel":null}
        """.trimMargin()

    @Test
    fun `filtro repete o parametro status e nao manda servico critico`() =
        runTest {
            calls(emptyPage).search(CallFilter(statuses = setOf(CallStatus.OPENED, CallStatus.IN_PROGRESS)), 0, 20).value()

            val url = lastRequest!!.url
            assertEquals(listOf("OPENED", "IN_PROGRESS"), url.queryParameterValues("status"))
            assertFalse(url.queryParameterNames.contains("criticalService"))
        }

    @Test
    fun `filtro sem status nao manda o parametro`() =
        runTest {
            calls(emptyPage).search(CallFilter(), 0, 20).value()

            assertFalse(lastRequest!!.url.queryParameterNames.contains("status"))
        }

    @Test
    fun `corpo da criacao so tem os campos do record do backend`() {
        val call =
            NewCall(
                description = "Monitor piscando desde a manhã.",
                status = CallStatus.CLOSED,
                priority = CallPriority.NORMAL,
                applicantId = "a",
                unitId = "u",
                localId = "l",
                equipmentId = null,
                categoryId = "c",
                subcategoryId = "s",
                technicianId = null,
            )

        val keys = ApiJson.encodeToJsonElement(call.toDto()).jsonObject.keys

        assertEquals(setOf("description", "status", "priority", "subcategoryId", "categoryId", "applicantId", "localId", "unitId"), keys)
    }

    @Test
    fun `pagina do spring traz os totais dentro de page`() =
        runTest {
            // O item de tipo único de api-fixtures (3 em estoque), numa página de 205.
            val item =
                ApiJson
                    .parseToJsonElement(resource("/api-fixtures/items.json"))
                    .jsonObject
                    .getValue("content")
                    .jsonArray
                    .first()
            val body =
                """{"content":[$item],"page":{"size":20,"number":0,"totalElements":205,"totalPages":11}}"""

            val page = RemoteStockRepository(api(body)).items("").value()

            assertEquals(205L, page.totalElements)
            assertEquals(11, page.totalPages)
            assertEquals(204L, page.notLoaded)
            assertTrue(page.items.single().isUnique)
            assertEquals(1, page.items.single().maxPerCall)
        }

    @Test
    fun `categorias tambem leem os totais`() =
        runTest {
            val body =
                """{"content":[{"id":"c1","name":"Hardware","code":"HW","active":true}],
                |"page":{"size":20,"number":0,"totalElements":21,"totalPages":2}}
                """.trimMargin()

            val page = RemoteCatalogRepository(api(body)).categories().value()

            assertEquals(20L, page.notLoaded)
        }

    @Test
    fun `papeis do backend sao lidos e o desconhecido ou nulo fica sem acesso`() {
        fun role(json: String) = ApiJson.decodeFromString<UserResponseDTO>(user(json)).toDomain().role

        assertEquals(Role.TECHNICAL_ADMIN, role("\"TECHNICAL_ADMIN\""))
        assertEquals(Role.SYSTEM_EXTERNAL, role("\"SYSTEM_EXTERNAL\""))
        assertEquals(Role.UNKNOWN, role("\"SECRETARY_ADMIN\""))
        assertEquals(Role.UNKNOWN, role("null"))
    }

    @Test
    fun `usuario sem nome, como o banco aceita, vira Sem nome`() {
        val user = ApiJson.decodeFromString<UserResponseDTO>(unnamedUser.toString()).toDomain()

        assertEquals("Sem nome", user.name)
        assertEquals("minimo@chamados.test", user.email)
        assertEquals(Role.UNKNOWN, user.role)
    }

    @Test
    fun `status e prioridade desconhecidos ou nulos nao derrubam a lista nem o detalhe`() {
        fun read(
            status: String,
            priority: String = "\"NORMAL\"",
        ) = ApiJson.decodeFromString<CallSummaryResponseDTO>(summary(status, priority)).toDomain()

        assertEquals(CallStatus.UNKNOWN, read("\"WAITING\"").status)
        assertEquals(CallStatus.UNKNOWN, read("null").status)
        assertEquals(CallPriority.UNKNOWN, read("\"OPENED\"", priority = "\"CRITICAL\"").priority)

        val detail =
            ApiJson
                .decodeFromString<CallResponseDTO>(
                    minimalCall("status" to JsonNull, "openedBy" to unnamedUser, "responsibleTechnician" to unnamedUser),
                ).toDomain()

        assertEquals(CallStatus.UNKNOWN, detail.status)
        assertEquals("Sem nome", detail.openedByName)
        assertEquals("Sem nome", detail.technician?.name)
    }

    @Test
    fun `data do backend e lida com seis casas e sem fracao`() {
        assertEquals(LocalDateTime.of(2026, 1, 1, 6, 0), "2026-01-01T06:00:00.000000".toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 1, 1, 6, 0), "2026-01-01T06:00:00".toLocalDateTime())
    }

    @Test
    fun `lista de chamados le o envelope com os totais dentro de page`() =
        runTest {
            val body =
                """{"content":[${summary("\"OPENED\"")}],"page":{"size":1,"number":2,"totalElements":7,"totalPages":7}}"""

            val page = calls(body).search(CallFilter(), 2, 1).value()

            assertEquals(2, page.page)
            assertEquals(7, page.totalPages)
            assertEquals(7L, page.totalElements)
        }

    @Test
    fun `historico sem autor, como o da atualizacao de status, e lido`() {
        val history =
            ApiJson.parseToJsonElement(
                """
                [{"id":"h","eventDate":"2026-01-02T10:00:00","itTeamId":null,"responsibleUser":null,
                "description":"Status alterado de Cancelado para Fechado na atualização do sistema."}]
                """.trimIndent(),
            )
        val dto = ApiJson.decodeFromString<CallResponseDTO>(minimalCall("status" to JsonPrimitive("CLOSED"), "history" to history))

        val entry = dto.toDomain().history.single()

        assertNull(entry.authorName)
        assertEquals("Status alterado de Cancelado para Fechado na atualização do sistema.", entry.description)
    }

    @Test
    fun `status e prioridade desconhecidos nunca vao a API`() =
        runTest {
            val newCall =
                NewCall(
                    description = "Monitor piscando desde a manhã.",
                    status = CallStatus.OPENED,
                    priority = CallPriority.UNKNOWN,
                    applicantId = "a",
                    unitId = "u",
                    localId = "l",
                    equipmentId = null,
                    categoryId = "c",
                    subcategoryId = "s",
                    technicianId = null,
                )
            val repository = calls(emptyPage)
            val unknownProgress = ProgressRequest("nota", CallStatus.UNKNOWN, null, emptyList())
            val unknownStatusCall = newCall.copy(status = CallStatus.UNKNOWN, priority = CallPriority.NORMAL)
            val cases =
                mapOf<String, suspend () -> AppResult<*>>(
                    "atendimento" to { repository.registerProgress("c", unknownProgress) },
                    "prioridade do novo chamado" to { repository.create(newCall) },
                    "status do novo chamado" to { repository.create(unknownStatusCall) },
                    "status do filtro" to { repository.search(CallFilter(statuses = setOf(CallStatus.OPENED, CallStatus.UNKNOWN)), 0, 20) },
                    "prioridade do filtro" to { repository.search(CallFilter(priority = CallPriority.UNKNOWN), 0, 20) },
                )

            cases.forEach { (case, action) ->
                lastRequest = null
                assertEquals(case, AppResult.Failure(DomainError.Unknown()), action())
                assertNull("$case: nenhuma requisição devia sair", lastRequest)
            }
        }

    @Test
    fun `campo opcional ausente ou nulo chega ao dominio sem inventar valor`() =
        runTest {
            val withoutApplicant = JsonObject(captured("call-minimal") - "applicant").toString()

            val detail = calls(withoutApplicant).detail("c")
            assertTrue(detail is AppResult.Success)
            assertNull((detail as AppResult.Success).value.applicant)
            val summary = ApiJson.decodeFromString<CallSummaryResponseDTO>(summary("\"OPENED\"", priority = "null"))
            assertNull(summary.priority)
            val user = ApiJson.decodeFromString<UserResponseDTO>(JsonObject(unnamedUser.jsonObject + ("email" to JsonNull)).toString())
            assertNull(user.email)
        }
}
