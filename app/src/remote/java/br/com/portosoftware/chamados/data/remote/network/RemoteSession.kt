package br.com.portosoftware.chamados.data.remote.network

import br.com.portosoftware.chamados.core.data.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estado de sessão compartilhado entre o AuthRepository e a pilha HTTP.
 * O interceptor lê a secretaria ativa daqui; o authenticator marca a sessão
 * como expirada quando a renovação falha.
 */
@Singleton
class RemoteSession
    @Inject
    constructor() {
        val state = MutableStateFlow<SessionState>(SessionState.Restoring)

        @Volatile var activeSecretaryId: String? = null

        /** Depois de Expired/SignedOut, só um novo login cria sessão; renovar seria inútil. */
        val canRenew: Boolean
            get() = state.value != SessionState.Expired && state.value != SessionState.SignedOut

        fun expire() {
            if (state.value is SessionState.Active || state.value is SessionState.ChoosingSecretary) {
                state.value = SessionState.Expired
            }
        }
    }
