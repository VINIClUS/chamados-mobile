package br.com.portosoftware.chamados.data.remote

import android.app.Application
import br.com.portosoftware.chamados.contract.model.CallResponseDTO
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Role
import br.com.portosoftware.chamados.core.model.RolePolicy
import br.com.portosoftware.chamados.core.model.ThemeMode
import br.com.portosoftware.chamados.core.model.TopLevel
import br.com.portosoftware.chamados.data.remote.network.PersistentCookieJar
import br.com.portosoftware.chamados.data.remote.network.RemoteSession
import br.com.portosoftware.chamados.di.NetworkModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Resposta fora do contrato, pelo caminho que o app usa (Retrofit com o conversor do `ApiJson`, `apiCall` e mapper),
 * nunca vira um estado válido no domínio: vira erro, ou vira desconhecido sem liberar ação. Os corpos são as respostas
 * capturadas (`api-responses`) com uma chave trocada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class InvalidPayloadTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun api(body: String): ChamadosApi {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        return NetworkModule.api(client, "https://chamados.test/api/")
    }

    private fun TestScope.calls(body: JsonElement) =
        RemoteCallRepository(api(body.toString()), context, UnconfinedTestDispatcher(testScheduler))

    private fun captured(name: String): JsonObject =
        Json.parseToJsonElement(requireNotNull(javaClass.getResource("/api-responses/$name.json")).readText()).jsonObject

    private fun JsonObject.with(vararg changes: Pair<String, JsonElement>) = JsonObject(this + changes)

    private val callMinimal by lazy { captured("call-minimal") }

    private val me by lazy { captured("call-full").getValue("openedBy").jsonObject }

    /** `calls-filter` com os metadados de página trocados. */
    private fun filterPage(vararg changes: Pair<String, JsonElement>): JsonObject {
        val page = captured("calls-filter")
        return page.with("page" to page.getValue("page").jsonObject.with(*changes))
    }

    private fun <T> AppResult<T>.value() = (this as AppResult.Success).value

    private class MemorySettings : SettingsRepository {
        override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)

        override fun lastSecretaryId(userId: String): Flow<String?> = flowOf(null)

        override suspend fun setThemeMode(mode: ThemeMode) = Unit

        override suspend fun setLastSecretaryId(
            userId: String,
            secretaryId: String?,
        ) = Unit
    }

    /** O estado da sessão depois de restaurá-la com [user] em `/auth/me`. */
    private suspend fun restoredWith(user: JsonObject): SessionState {
        val session = RemoteSession()
        RemoteAuthRepository(api(user.toString()), session, PersistentCookieJar(context), MemorySettings()).restore()
        return session.state.value
    }

    @Test
    fun `status desconhecido ou nulo chega ao detalhe como desconhecido, sem transicao, atendimento nem anexo`() =
        runTest {
            val unknownBody = callMinimal.with("status" to JsonPrimitive("WAITING"), "priority" to JsonPrimitive("CRITICAL"))
            val unknown = calls(unknownBody).detail("c").value()
            val nullStatus = calls(callMinimal.with("status" to JsonNull)).detail("c").value()

            for (call in listOf(unknown, nullStatus)) {
                assertEquals(CallStatus.UNKNOWN, call.status)
                assertEquals(emptyList<CallStatus>(), CallRules.allowedTransitions(call.status))
                assertEquals(emptyList<CallStatus>(), CallRules.progressOptions(call.status))
                assertFalse(CallRules.acceptsAttachments(call.status))
            }
            assertEquals(CallPriority.UNKNOWN, unknown.priority)
        }

    @Test
    fun `status e prioridade desconhecidos chegam a lista como desconhecidos`() =
        runTest {
            val page = captured("calls-filter")
            val content = page.getValue("content").jsonArray
            val changed =
                listOf(
                    content[0].jsonObject.with("status" to JsonPrimitive("WAITING")),
                    content[1].jsonObject.with("priority" to JsonPrimitive("CRITICAL")),
                )

            val items = calls(page.with("content" to JsonArray(changed))).search(CallFilter(), 0, 20).value().items

            assertEquals(CallStatus.UNKNOWN, items[0].status)
            assertEquals(CallPriority.UNKNOWN, items[1].priority)
        }

    @Test
    fun `papel desconhecido ou nulo entra so com o Perfil, sem criar, atender, anexar nem listar tecnicos`() =
        runTest {
            for (role in listOf(JsonPrimitive("SECRETARY_ADMIN"), JsonNull)) {
                val state = restoredWith(me.with("role" to role))

                assertTrue("$role: $state", state is SessionState.Active)
                val policy = RolePolicy((state as SessionState.Active).user.role)
                assertEquals(Role.UNKNOWN, policy.role)
                assertEquals(listOf(TopLevel.PROFILE), policy.destinations)
                assertFalse(policy.canCreateCall || policy.canAttend || policy.canUploadAttachments || policy.canListTechnicians)
            }
        }

    /**
     * Toda chave de resposta vem, com `null` quando não há valor. Sem a chave, a leitura falha: um chamado aberto sem
     * `responsibleTechnician` não pode chegar ao domínio como "sem responsável", que oferece "Assumir". O controle lê o
     * mesmo corpo com `explicitNulls = false`, a configuração que fazia isso.
     */
    @Test
    fun `chave anulavel ausente derruba a leitura em vez de liberar Assumir`() =
        runTest {
            val body = JsonObject(callMinimal.with("status" to JsonPrimitive("OPENED")) - "responsibleTechnician")
            val tolerant = Json(ApiJson) { explicitNulls = false }.decodeFromString<CallResponseDTO>(body.toString()).toDomain()
            assertTrue(tolerant.status == CallStatus.OPENED && tolerant.isUnassigned)

            assertEquals(AppResult.Failure(DomainError.Unknown()), calls(body).detail("c"))
            assertEquals(SessionState.SignedOut, restoredWith(JsonObject(me - "name")))
        }

    @Test
    fun `data fora do formato do contrato vira erro, e nao data padrao nem data errada`() =
        runTest {
            val history = captured("call-full").getValue("history").jsonArray
            val malformed =
                listOf(
                    "2026-11-06T22:48:47Z",
                    "2026-11-06T22:48:47.095305-03:00",
                    "2026-02-30T10:00:00",
                    "2026-01-01T24:00:00",
                    "2026-01-01T10:00:00.1234567891",
                    "2026-01-01",
                    "06/11/2026 22:48:47",
                    "",
                )
            assertTrue("call-full sem histórico", history.isNotEmpty())

            for (date in malformed.map(::JsonPrimitive)) {
                val event = JsonArray(listOf(history[0].jsonObject.with("eventDate" to date)))
                val bodies = listOf("openingDate" to date, "completionDate" to date, "history" to event).map { callMinimal.with(it) }
                for (body in bodies) {
                    assertEquals("$date", AppResult.Failure(DomainError.Unknown()), calls(body).detail("c"))
                }
            }
        }

    /**
     * `LocalDateTime.parse` lê também, fora do `pattern` do contrato: sem segundos, com `t` minúsculo, com ano com sinal
     * e com ponto final sem casas. O instante lido é o escrito, sem nada inventado, e por isso as tolerâncias ficam.
     */
    @Test
    fun `data do contrato e lida com 0 a 9 casas, e as tolerancias do ISO leem o instante escrito`() {
        val at = LocalDateTime.of(2026, 11, 6, 22, 48, 47)

        assertEquals(at.withNano(95_305_000), "2026-11-06T22:48:47.095305".toLocalDateTime())
        assertEquals(at.withNano(95_305_123), "2026-11-06T22:48:47.095305123".toLocalDateTime())
        assertEquals(at.withNano(100_000_000), "2026-11-06T22:48:47.1".toLocalDateTime())
        assertEquals(at, "2026-11-06T22:48:47".toLocalDateTime())
        assertEquals(at.withSecond(0), "2026-11-06T22:48".toLocalDateTime())
        assertEquals(at, "2026-11-06t22:48:47".toLocalDateTime())
        assertEquals(at.withYear(12026), "+12026-11-06T22:48:47".toLocalDateTime())
        assertEquals(at.withYear(-1), "-0001-11-06T22:48:47".toLocalDateTime())
        assertEquals(at, "2026-11-06T22:48:47.".toLocalDateTime())
    }

    @Test
    fun `pagina com numero acima de Int vira erro, e nao numero truncado`() =
        runTest {
            val beyond = JsonPrimitive(Int.MAX_VALUE.toLong() + 1)
            val wrapsToOne = JsonPrimitive(1L shl 32 or 1L)

            for (change in listOf("totalPages" to beyond, "totalPages" to wrapsToOne, "number" to beyond)) {
                assertEquals("$change", AppResult.Failure(DomainError.Unknown()), calls(filterPage(change)).search(CallFilter(), 0, 20))
            }
            val largest = calls(filterPage("totalPages" to JsonPrimitive(Int.MAX_VALUE))).search(CallFilter(), 0, 20).value()
            assertEquals(Int.MAX_VALUE, largest.totalPages)
            assertFalse(largest.isLast)
        }
}
