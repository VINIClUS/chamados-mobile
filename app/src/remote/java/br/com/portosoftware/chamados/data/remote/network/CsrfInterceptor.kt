package br.com.portosoftware.chamados.data.remote.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Login e refresh já buscam o próprio token (RemoteAuthRepository e TokenRefreshAuthenticator). */
private val SELF_MANAGED_PATHS = listOf("/auth/login", "/auth/refresh")

private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")

/**
 * Busca um `XSRF-TOKEN` novo (`GET /auth/csrf`) antes de cada POST, PUT, PATCH e DELETE.
 *
 * O backend só troca o token no login e no refresh (`SecurityConfig` e `AuthController`). Buscar antes
 * de cada mutação custa um GET e protege o app de um cookie vencido ou apagado no servidor: sem token
 * válido, a mutação leva 403.
 *
 * Deve vir antes do [SessionHeadersInterceptor], que copia o cookie novo para o header. O lock cobre
 * a busca e a requisição: duas mutações simultâneas trocariam o cookie uma da outra. O
 * [TokenRefreshAuthenticator] não cobre esse 403: ele só age em 401, de sessão vencida. Fica só
 * no cliente principal: o `refreshClient` não passa por aqui.
 */
@Singleton
class CsrfInterceptor
    @Inject
    constructor(
        @Named("refreshClient") private val csrfClient: OkHttpClient,
        @Named("baseUrl") private val baseUrl: String,
    ) : Interceptor {
        private val lock = Any()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.method in SAFE_METHODS || SELF_MANAGED_PATHS.any { request.url.encodedPath.endsWith(it) }) {
                return chain.proceed(request)
            }
            return synchronized(lock) {
                csrfClient
                    .newCall(
                        Request
                            .Builder()
                            .url(baseUrl + "auth/csrf")
                            .get()
                            .build(),
                    ).execute()
                    .close()
                chain.proceed(request)
            }
        }
    }
