package br.com.portosoftware.chamados.data.remote

import br.com.portosoftware.chamados.contract.model.AuthRequest
import br.com.portosoftware.chamados.contract.model.ResetPasswordRequestDTO
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.core.model.User
import br.com.portosoftware.chamados.data.remote.network.PersistentCookieJar
import br.com.portosoftware.chamados.data.remote.network.RemoteSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteAuthRepository
    @Inject
    constructor(
        private val api: ChamadosApi,
        private val remoteSession: RemoteSession,
        private val cookieJar: PersistentCookieJar,
        private val settings: SettingsRepository,
    ) : AuthRepository {
        override val session: StateFlow<SessionState> = remoteSession.state.asStateFlow()

        override suspend fun restore() {
            // Com cookies salvos, /auth/me responde (renovando via authenticator se preciso).
            when (val me = apiCall { api.me() }) {
                is AppResult.Success -> enter(me.value.toDomain(), autoSelect = true)
                is AppResult.Failure -> remoteSession.state.value = SessionState.SignedOut
            }
        }

        override suspend fun login(
            email: String,
            password: String,
        ): AppResult<Unit> {
            apiCall { api.csrf().successOrThrow() }
            val login = apiCall { api.login(AuthRequest(email = email.trim(), password = password)) }
            if (login is AppResult.Failure) {
                // Só 401 é credencial errada; 403 aqui é CSRF/sessão, e a mensagem do servidor explica.
                return if (login.error is DomainError.Unauthorized) {
                    AppResult.Failure(DomainError.Validation("E-mail ou senha inválidos."))
                } else {
                    login
                }
            }
            return when (val me = apiCall { api.me() }) {
                is AppResult.Success -> {
                    enter(me.value.toDomain(), autoSelect = false)
                    AppResult.Success(Unit)
                }

                is AppResult.Failure -> {
                    me
                }
            }
        }

        private suspend fun enter(
            user: User,
            autoSelect: Boolean,
        ) {
            val last = settings.lastSecretaryId(user.id).first()
            val remembered = user.secretaries.firstOrNull { it.id == last }
            when {
                user.secretaries.size == 1 -> selectSecretary(user, user.secretaries.first())
                autoSelect && remembered != null -> selectSecretary(user, remembered)
                else -> remoteSession.state.value = SessionState.ChoosingSecretary(user)
            }
        }

        override suspend fun selectSecretary(secretary: Secretary) {
            val user =
                when (val current = remoteSession.state.value) {
                    is SessionState.ChoosingSecretary -> current.user
                    is SessionState.Active -> current.user
                    else -> return
                }
            selectSecretary(user, secretary)
        }

        private suspend fun selectSecretary(
            user: User,
            secretary: Secretary,
        ) {
            remoteSession.activeSecretaryId = secretary.id
            settings.setLastSecretaryId(user.id, secretary.id)
            remoteSession.state.value = SessionState.Active(user, secretary)
        }

        override suspend fun requestPasswordReset(email: String): AppResult<Unit> {
            apiCall { api.csrf().successOrThrow() }
            // O backend responde sucesso mesmo para e-mails desconhecidos; erro aqui é rede,
            // e-mail malformado (400) ou CSRF, e o usuário precisa saber.
            return apiCall { api.forgotPassword(ResetPasswordRequestDTO(email.trim())).successOrThrow() }
        }

        override suspend fun logout() {
            apiCall { api.logout() }
            cookieJar.clear()
            remoteSession.activeSecretaryId = null
            remoteSession.state.value = SessionState.SignedOut
        }

        override fun acknowledgeExpired() {
            cookieJar.clear()
            remoteSession.activeSecretaryId = null
            remoteSession.state.value = SessionState.SignedOut
        }
    }
