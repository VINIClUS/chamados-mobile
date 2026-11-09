package br.com.portosoftware.chamados.ui

import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.ThemeMode
import br.com.portosoftware.chamados.data.fake.FakeAuthRepository
import br.com.portosoftware.chamados.data.fake.FakeBackend
import br.com.portosoftware.chamados.data.fake.FakeCallRepository
import br.com.portosoftware.chamados.data.fake.FakeCatalogRepository
import br.com.portosoftware.chamados.data.fake.FakeData
import br.com.portosoftware.chamados.data.fake.FakeStockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemorySettings : SettingsRepository {
    override val themeMode: StateFlow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)
    private val lastSecretaryIds = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun setThemeMode(mode: ThemeMode) {
        (themeMode as MutableStateFlow).value = mode
    }

    override fun lastSecretaryId(userId: String): Flow<String?> = lastSecretaryIds.map { it[userId] }

    override suspend fun setLastSecretaryId(
        userId: String,
        secretaryId: String?,
    ) {
        lastSecretaryIds.update { if (secretaryId == null) it - userId else it + (userId to secretaryId) }
    }
}

/** O mesmo "servidor" em memória do flavor fake, montado à mão para testes de ViewModel. */
class FakeEnv {
    val backend = FakeBackend()
    val settings = InMemorySettings()
    val auth = FakeAuthRepository(backend, settings)
    val calls = FakeCallRepository(backend)
    val catalog = FakeCatalogRepository(backend)
    val stock = FakeStockRepository(backend)

    suspend fun loginAs(email: String) {
        auth.login(email, "demo")
    }
}
