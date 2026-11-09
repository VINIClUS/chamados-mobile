package br.com.portosoftware.chamados.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settings by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SettingsRepository {
        private val themeKey = stringPreferencesKey("theme_mode")

        // Uma chave por usuário: com uma só, quem entrava depois via a secretaria do anterior
        // como "Última usada".
        private fun secretaryKey(userId: String) = stringPreferencesKey("last_secretary_id_$userId")

        override val themeMode: Flow<ThemeMode> =
            context.settings.data.map { prefs ->
                prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
            }

        override fun lastSecretaryId(userId: String): Flow<String?> = context.settings.data.map { it[secretaryKey(userId)] }

        override suspend fun setThemeMode(mode: ThemeMode) {
            context.settings.edit { it[themeKey] = mode.name }
        }

        override suspend fun setLastSecretaryId(
            userId: String,
            secretaryId: String?,
        ) {
            val key = secretaryKey(userId)
            context.settings.edit { if (secretaryId == null) it.remove(key) else it[key] = secretaryId }
        }
    }
