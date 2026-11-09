package br.com.portosoftware.chamados.data.remote.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Adiciona `X-XSRF-TOKEN` (lido do cookie) e `X-Active-Secretary-Id` em toda requisição. */
class SessionHeadersInterceptor
    @Inject
    constructor(
        private val cookieJar: PersistentCookieJar,
        private val session: RemoteSession,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val builder = chain.request().newBuilder()
            cookieJar.valueOf(XSRF_COOKIE)?.let { builder.header(XSRF_HEADER, it) }
            session.activeSecretaryId?.let { builder.header(SECRETARY_HEADER, it) }
            return chain.proceed(builder.build())
        }

        companion object {
            const val XSRF_COOKIE = "XSRF-TOKEN"
            const val XSRF_HEADER = "X-XSRF-TOKEN"
            const val SECRETARY_HEADER = "X-Active-Secretary-Id"
        }
    }
