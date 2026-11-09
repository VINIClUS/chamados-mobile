package br.com.portosoftware.chamados.di

import br.com.portosoftware.chamados.BuildConfig
import br.com.portosoftware.chamados.core.data.ApplicantRepository
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.data.CatalogRepository
import br.com.portosoftware.chamados.core.data.StockRepository
import br.com.portosoftware.chamados.data.remote.ApiJson
import br.com.portosoftware.chamados.data.remote.ChamadosApi
import br.com.portosoftware.chamados.data.remote.RemoteApplicantRepository
import br.com.portosoftware.chamados.data.remote.RemoteAuthRepository
import br.com.portosoftware.chamados.data.remote.RemoteCallRepository
import br.com.portosoftware.chamados.data.remote.RemoteCatalogRepository
import br.com.portosoftware.chamados.data.remote.RemoteStockRepository
import br.com.portosoftware.chamados.data.remote.network.CsrfInterceptor
import br.com.portosoftware.chamados.data.remote.network.PersistentCookieJar
import br.com.portosoftware.chamados.data.remote.network.SessionHeadersInterceptor
import br.com.portosoftware.chamados.data.remote.network.TokenRefreshAuthenticator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/** Flavor "remote": liga os mesmos contratos às implementações Retrofit. */
@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds fun auth(impl: RemoteAuthRepository): AuthRepository

    @Binds fun calls(impl: RemoteCallRepository): CallRepository

    @Binds fun catalog(impl: RemoteCatalogRepository): CatalogRepository

    @Binds fun applicants(impl: RemoteApplicantRepository): ApplicantRepository

    @Binds fun stock(impl: RemoteStockRepository): StockRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_TIMEOUT_S = 15L

    // Uploads de anexos podem demorar mais que o handshake.
    private const val READ_TIMEOUT_S = 30L

    @Provides
    @Named("baseUrl")
    fun baseUrl(): String = BuildConfig.API_BASE_URL

    /** Cliente só para csrf/refresh: mesmo CookieJar, sem authenticator (evita recursão). */
    @Provides @Singleton
    @Named("refreshClient")
    fun refreshClient(cookieJar: PersistentCookieJar): OkHttpClient =
        OkHttpClient
            .Builder()
            .cookieJar(cookieJar)
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton
    fun okHttp(
        cookieJar: PersistentCookieJar,
        csrf: CsrfInterceptor,
        headers: SessionHeadersInterceptor,
        authenticator: TokenRefreshAuthenticator,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .cookieJar(cookieJar)
            // Nesta ordem: o CsrfInterceptor renova o cookie e o SessionHeadersInterceptor o copia para o header.
            .addInterceptor(csrf)
            .addInterceptor(headers)
            .authenticator(authenticator)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }.connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton
    fun api(
        client: OkHttpClient,
        @Named("baseUrl") baseUrl: String,
    ): ChamadosApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ChamadosApi::class.java)
}
