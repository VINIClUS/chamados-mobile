package br.com.portosoftware.chamados.data.remote.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val BASE_URL = "http://127.0.0.1:8181/api/"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class CsrfInterceptorTest {
    private lateinit var cookieJar: PersistentCookieJar
    private var csrfCalls = 0
    private val sentTokens = mutableListOf<String?>()

    private fun ok(request: Request) =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("")
            .body(ByteArray(0).toResponseBody())
            .build()

    /** `GET /auth/csrf` sem rede: cada chamada grava um token novo no CookieJar, como o Set-Cookie do backend. */
    private val csrfServer =
        Interceptor { chain ->
            csrfCalls++
            val url = BASE_URL.toHttpUrl()
            cookieJar.saveFromResponse(url, listOfNotNull(Cookie.parse(url, "XSRF-TOKEN=t$csrfCalls; Path=/")))
            ok(chain.request())
        }

    /** O "backend" das outras rotas só registra o header recebido. */
    private val apiServer =
        Interceptor { chain ->
            sentTokens += chain.request().header(SessionHeadersInterceptor.XSRF_HEADER)
            ok(chain.request())
        }

    private val client by lazy {
        OkHttpClient
            .Builder()
            .addInterceptor(CsrfInterceptor(OkHttpClient.Builder().addInterceptor(csrfServer).build(), BASE_URL))
            .addInterceptor(SessionHeadersInterceptor(cookieJar, RemoteSession()))
            .addInterceptor(apiServer)
            .build()
    }

    private fun send(
        method: String,
        path: String,
    ) {
        val body = if (method == "GET") null else "{}".toRequestBody("application/json".toMediaType())
        client
            .newCall(
                Request
                    .Builder()
                    .url(BASE_URL + path)
                    .method(method, body)
                    .build(),
            ).execute()
            .close()
    }

    @Before
    fun setUp() {
        cookieJar = PersistentCookieJar(RuntimeEnvironment.getApplication())
        cookieJar.clear()
    }

    @Test
    fun `mutacao busca um token novo e o envia no header`() {
        send("PATCH", "calls/c1/progress")
        send("POST", "auth/logout")

        assertEquals(2, csrfCalls)
        assertEquals(listOf("t1", "t2"), sentTokens)
    }

    @Test
    fun `GET, login e refresh nao buscam token`() {
        send("GET", "calls/filter")
        send("POST", "auth/login")
        send("POST", "auth/refresh")

        assertEquals(0, csrfCalls)
    }
}
