package br.com.portosoftware.chamados.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        settings: SettingsRepository,
    ) : ViewModel() {
        val session: StateFlow<SessionState> = auth.session

        /** Mantém o app visível por trás do diálogo de sessão expirada, inclusive depois de girar a tela. */
        val lastActive: StateFlow<SessionState.Active?> =
            auth.session
                .runningFold(null as SessionState.Active?) { last, s ->
                    when (s) {
                        is SessionState.Active -> s
                        SessionState.SignedOut -> null
                        else -> last
                    }
                }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val themeMode: StateFlow<ThemeMode> =
            settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

        /**
         * Um ViewModelStore por sessão (usuário + secretaria). O NavHost guarda os ViewModels da pilha
         * no store do LocalViewModelStoreOwner; com o da Activity, nada os limpava: cada login, troca de
         * secretaria ou sessão expirada deixava a pilha anterior viva, e cada mutação recarregava o
         * Início de todas elas (5 GET /dashboard/calls depois de um Assumir).
         */
        private val sessionStores = mutableMapOf<Pair<String, String>, ViewModelStoreOwner>()

        init {
            viewModelScope.launch { auth.restore() }
            // Expired continua com o store da última sessão, que segue visível por trás do diálogo.
            viewModelScope.launch { lastActive.collect { releaseSessionsExcept(it?.key) } }
        }

        /** Mesmo dono para a mesma sessão, inclusive depois de girar a tela. */
        fun sessionStoreOwner(active: SessionState.Active): ViewModelStoreOwner =
            sessionStores.getOrPut(active.key) {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }

        fun acknowledgeExpired() = auth.acknowledgeExpired()

        override fun onCleared() = releaseSessionsExcept(null)

        private fun releaseSessionsExcept(current: Pair<String, String>?) {
            val stale = sessionStores.keys.filter { it != current }
            stale.forEach { key -> sessionStores.remove(key)?.viewModelStore?.clear() }
        }

        private val SessionState.Active.key get() = user.id to secretary.id
    }
