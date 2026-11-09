package br.com.portosoftware.chamados.data.remote.network

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda `access_token`, `refresh_token` e `XSRF-TOKEN` entre execuções do app.
 * Os cookies de sessão são HttpOnly no navegador; aqui eles só existem dentro do
 * processo do app (armazenamento privado). Para produção, trocar o SharedPreferences
 * por EncryptedFile/Jetpack Security.
 */
@Singleton
class PersistentCookieJar
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : CookieJar {
        private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
        private val cookies = mutableMapOf<String, Cookie>()

        init {
            prefs.all.forEach { (key, value) ->
                val url = prefs.getString("url:$key", null)
                if (!key.startsWith("url:") && value is String && url != null) {
                    url.toHttpUrlOrNull()?.let { httpUrl ->
                        Cookie.parse(httpUrl, value)?.let { cookies[key] = it }
                    }
                }
            }
        }

        @Synchronized
        override fun saveFromResponse(
            url: HttpUrl,
            cookies: List<Cookie>,
        ) {
            prefs.edit {
                for (cookie in cookies) {
                    val key = "${cookie.domain}|${cookie.path}|${cookie.name}"
                    if (cookie.expiresAt < System.currentTimeMillis()) {
                        this@PersistentCookieJar.cookies.remove(key)
                        remove(key).remove("url:$key")
                    } else {
                        this@PersistentCookieJar.cookies[key] = cookie
                        putString(key, cookie.toString()).putString("url:$key", url.toString())
                    }
                }
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookies.entries.removeAll { it.value.expiresAt < now }
            return cookies.values.filter { it.matches(url) }
        }

        @Synchronized
        fun valueOf(name: String): String? = cookies.values.firstOrNull { it.name == name }?.value

        @Synchronized
        fun clear() {
            cookies.clear()
            prefs.edit { clear() }
        }
    }
