package br.com.portosoftware.chamados.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.DomainError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val forgotOpen: Boolean = false,
    val forgotEmail: String = "",
    val forgotSending: Boolean = false,
    val message: String? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !loading
}

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(LoginUiState())
        val state: StateFlow<LoginUiState> = _state.asStateFlow()

        fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

        fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

        fun submit() {
            val s = _state.value
            if (!s.canSubmit) return
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                // Sucesso não precisa de navegação aqui: o AuthRepository muda o SessionState
                // e a raiz do app troca de tela.
                when (val result = auth.login(s.email, s.password)) {
                    // A senha não fica na memória para o próximo login depois de sair da conta.
                    is AppResult.Success -> {
                        _state.update { LoginUiState(email = it.email) }
                    }

                    // Só credencial recusada fica no campo de senha. Rede ou servidor fora iam para o
                    // mesmo lugar e pareciam senha errada; esses vão para o Snackbar.
                    is AppResult.Failure -> {
                        _state.update {
                            if (result.error is DomainError.Validation) {
                                it.copy(loading = false, error = result.error.message)
                            } else {
                                it.copy(loading = false, message = result.error.message)
                            }
                        }
                    }
                }
            }
        }

        fun openForgot() = _state.update { it.copy(forgotOpen = true, forgotEmail = it.email) }

        fun closeForgot() = _state.update { it.copy(forgotOpen = false) }

        fun onForgotEmailChange(value: String) = _state.update { it.copy(forgotEmail = value) }

        fun sendForgot() {
            val email = _state.value.forgotEmail
            if (email.isBlank()) return
            _state.update { it.copy(forgotSending = true) }
            viewModelScope.launch {
                val result = auth.requestPasswordReset(email)
                _state.update {
                    it.copy(
                        forgotSending = false,
                        // Só reabre se o usuário ainda estiver com o diálogo aberto.
                        forgotOpen = it.forgotOpen && result is AppResult.Failure,
                        // Resposta neutra: o backend não revela se o e-mail existe.
                        message =
                            if (result is AppResult.Success) {
                                "Se o e-mail estiver cadastrado, você receberá um link para redefinir a senha."
                            } else {
                                (result as AppResult.Failure)
                                    .error.message
                            },
                    )
                }
            }
        }

        fun messageShown() = _state.update { it.copy(message = null) }
    }
