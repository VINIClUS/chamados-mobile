package br.com.portosoftware.chamados.data.remote.network

import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_MULT_CHOICE
import java.net.HttpURLConnection.HTTP_OK
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Em 401, renova a sessão uma única vez, como `src/web/src/api/axios.ts`:
 * `GET /auth/csrf` (cookie XSRF novo) e depois `POST /auth/refresh`; se o refresh
 * responder 403, repete o par uma vez. Usa um cliente sem authenticator (não recursa)
 * e um lock para que requisições concorrentes compartilhem a mesma renovação.
 *
 * Só uma recusa do servidor encerra a sessão. Falha de rede ou 5xx durante a renovação
 * vira [IOException] na requisição original (erro de conexão na tela), sem apagar o
 * refresh_token, que continua válido.
 */
private val AUTH_PATHS = listOf("/auth/login", "/auth/refresh", "/auth/csrf")

private const val REFRESH_COOKIE = "refresh_token"

/** O refresh que responde 403 (CSRF velho) é repetido uma vez, como no axios.ts. */
private const val REFRESH_ATTEMPTS = 2

@Singleton
class TokenRefreshAuthenticator
    @Inject
    constructor(
        @Named("refreshClient") private val refreshClient: OkHttpClient,
        @Named("baseUrl") private val baseUrl: String,
        private val cookieJar: PersistentCookieJar,
        private val session: RemoteSession,
    ) : Authenticator {
        private val lock = Any()

        @Volatile private var lastRefreshAt = 0L

        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            val path = response.request.url.encodedPath
            if (AUTH_PATHS.any { path.endsWith(it) }) return null
            // Sessão já encerrada (diálogo aberto) ou sem refresh_token: não há o que renovar.
            if (!session.canRenew || cookieJar.valueOf(REFRESH_COOKIE) == null) {
                session.expire()
                return null
            }
            // priorResponse != null: já repetimos com a sessão renovada e ainda assim deu 401.
            val renewed = response.priorResponse == null && renewOnce(response.sentRequestAtMillis)
            if (!renewed) session.expire()
            return if (renewed) withFreshCsrf(response.request) else null
        }

        private fun renewOnce(requestStartedAt: Long): Boolean =
            synchronized(lock) {
                // Outra requisição já renovou depois que esta saiu: só repetir.
                lastRefreshAt > requestStartedAt ||
                    refresh().also { if (it) lastRefreshAt = System.currentTimeMillis() }
            }

        private fun withFreshCsrf(request: Request): Request {
            val builder = request.newBuilder()
            cookieJar.valueOf(SessionHeadersInterceptor.XSRF_COOKIE)?.let {
                builder.header(SessionHeadersInterceptor.XSRF_HEADER, it)
            }
            return builder.build()
        }

        /** true se renovou, false se o servidor recusou; [IOException] se não deu para saber. */
        private fun refresh(): Boolean {
            var attempts = 0
            var code: Int
            do {
                fetchCsrf()
                code = post("auth/refresh")
                attempts++
            } while (code == HTTP_FORBIDDEN && attempts < REFRESH_ATTEMPTS)
            if (code >= HTTP_INTERNAL_ERROR) throw IOException("Servidor indisponível ao renovar a sessão (HTTP $code).")
            return code in HTTP_OK until HTTP_MULT_CHOICE
        }

        private fun fetchCsrf() {
            refreshClient
                .newCall(
                    Request
                        .Builder()
                        .url(baseUrl + "auth/csrf")
                        .get()
                        .build(),
                ).execute()
                .close()
        }

        private fun post(path: String): Int {
            val request =
                Request
                    .Builder()
                    .url(baseUrl + path)
                    .post(ByteArray(0).toRequestBody())
                    .apply {
                        cookieJar
                            .valueOf(
                                SessionHeadersInterceptor.XSRF_COOKIE,
                            )?.let { header(SessionHeadersInterceptor.XSRF_HEADER, it) }
                    }.build()
            return refreshClient.newCall(request).execute().use { it.code }
        }
    }
