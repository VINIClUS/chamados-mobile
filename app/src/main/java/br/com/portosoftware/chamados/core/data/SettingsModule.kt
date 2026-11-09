package br.com.portosoftware.chamados.core.data

import br.com.portosoftware.chamados.core.data.local.DataStoreSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface SettingsModule {
    @Binds
    fun bindSettings(impl: DataStoreSettingsRepository): SettingsRepository
}
