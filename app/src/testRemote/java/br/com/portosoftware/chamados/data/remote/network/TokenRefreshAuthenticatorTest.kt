package br.com.portosoftware.chamados.data.remote.network

import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.model.Role
import br.com.portosoftware.chamados.core.model.User
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

private const val BASE_URL = "http://10.0.2.2:8181/api/"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class TokenRefreshAuthenticatorTest {
    private lateinit var cookieJar: PersistentCookieJar
    private lateinit var session: RemoteSession
    private val refreshCodes = ArrayDeque<Int>()
    private var refreshCalls = 0
    private var networkDown = false

    /** Responde /auth/csrf e /auth/refresh sem rede, com os códigos enfileirados em [refreshCodes]. */
    private val fakeServer =
        Interceptor { chain ->
            if (networkDown) throw IOException("sem rede")
            val request = chain.request()
            val code =
                if (request.url.encodedPath.endsWith("auth/refresh")) {
                    refreshCalls++
                    refreshCodes.removeFirst()
                } else {
                    200
                }
            response(request, code)
        }

    private fun response(
        request: Request,
        code: Int,
    ) = Response
        .Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(ByteArray(0).toResponseBody())
        .build()

    private fun authenticator() =
        TokenRefreshAuthenticator(
            refreshClient = OkHttpClient.Builder().addInterceptor(fakeServer).build(),
            baseUrl = BASE_URL,
            cookieJar = cookieJar,
            session = session,
        )

    private fun unauthorized(path: String = "calls") =
        response(
            Request.Builder().url(BASE_URL + path).build(),
            401,
        )

    @Before
    fun setUp() {
        cookieJar = PersistentCookieJar(RuntimeEnvironment.getApplication())
        cookieJar.clear()
        val url = BASE_URL.toHttpUrl()
        cookieJar.saveFromResponse(url, listOfNotNull(Cookie.parse(url, "refresh_token=r1; Path=/api/auth")))
        session = RemoteSession()
        session.state.value = SessionState.ChoosingSecretary(User("u", "Ana", "ana@x", Role.TECHNICAL, emptyList()))
    }

    @Test
    fun `refresh aceito repete a requisicao`() {
        refreshCodes += 200

        assertNotNull(authenticator().authenticate(null, unauthorized()))
        assertEquals(SessionState.ChoosingSecretary::class, session.state.value::class)
    }

    @Test
    fun `refresh recusado encerra a sessao`() {
        refreshCodes += listOf(403, 401)

        assertNull(authenticator().authenticate(null, unauthorized()))
        assertEquals(SessionState.Expired, session.state.value)
    }

    @Test
    fun `falha de rede ou 5xx na renovacao nao encerra a sessao`() {
        networkDown = true
        assertThrows(IOException::class.java) { authenticator().authenticate(null, unauthorized()) }
        networkDown = false
        refreshCodes += 503
        assertThrows(IOException::class.java) { authenticator().authenticate(null, unauthorized()) }

        assertEquals(SessionState.ChoosingSecretary::class, session.state.value::class)
    }

    @Test
    fun `sem refresh_token ou com a sessao ja expirada nao chama o servidor`() {
        session.state.value = SessionState.Expired
        assertNull(authenticator().authenticate(null, unauthorized()))

        session.state.value = SessionState.ChoosingSecretary(User("u", "Ana", "ana@x", Role.TECHNICAL, emptyList()))
        cookieJar.clear()
        assertNull(authenticator().authenticate(null, unauthorized()))

        assertEquals(0, refreshCalls)
        assertEquals(SessionState.Expired, session.state.value)
    }
}
