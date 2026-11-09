package br.com.portosoftware.chamados.core.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/** Dispatcher para I/O bloqueante (leitura de arquivos), injetável para os testes trocarem. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    // Este é o ponto de injeção: o único lugar que referencia Dispatchers.IO diretamente.
    @Suppress("InjectDispatcher")
    @Provides
    @IoDispatcher
    fun io(): CoroutineDispatcher = Dispatchers.IO
}
