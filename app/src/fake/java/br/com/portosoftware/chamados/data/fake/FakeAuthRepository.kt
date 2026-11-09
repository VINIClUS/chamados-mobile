package br.com.portosoftware.chamados.data.fake

import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Secretary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAuthRepository
    @Inject
    constructor(
        private val backend: FakeBackend,
        private val settings: SettingsRepository,
    ) : AuthRepository {
        private val _session = MutableStateFlow<SessionState>(SessionState.Restoring)
        override val session: StateFlow<SessionState> = _session.asStateFlow()

        override suspend fun restore() {
            // O fake não persiste sessão entre execuções: sempre começa no login.
            _session.value = SessionState.SignedOut
        }

        override suspend fun login(
            email: String,
            password: String,
        ): AppResult<Unit> =
            backend.call {
                val user = FakeData.users.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
                when {
                    password == "offline" -> {
                        fail(DomainError.Network())
                    }

                    user == null || password.isBlank() -> {
                        fail(DomainError.Validation("E-mail ou senha inválidos."))
                    }

                    else -> {
                        currentUser = user
                        if (user.secretaries.size == 1) {
                            activeSecretary = user.secretaries.first()
                            _session.value = SessionState.Active(user, user.secretaries.first())
                        } else {
                            _session.value = SessionState.ChoosingSecretary(user)
                        }
                        ok(Unit)
                    }
                }
            }

        override suspend fun selectSecretary(secretary: Secretary) {
            val user = backend.currentUser ?: return
            backend.activeSecretary = secretary
            settings.setLastSecretaryId(user.id, secretary.id)
            _session.value = SessionState.Active(user, secretary)
        }

        override suspend fun requestPasswordReset(email: String): AppResult<Unit> = backend.call { ok(Unit) }

        override suspend fun logout() {
            backend.currentUser = null
            backend.activeSecretary = null
            _session.value = SessionState.SignedOut
        }

        override fun acknowledgeExpired() {
            _session.value = SessionState.SignedOut
        }
    }
