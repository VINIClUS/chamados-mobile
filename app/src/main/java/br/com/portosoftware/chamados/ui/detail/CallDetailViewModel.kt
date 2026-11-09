package br.com.portosoftware.chamados.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.data.activeSession
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.RolePolicy
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.dataOrNull
import br.com.portosoftware.chamados.ui.common.toUiState
import br.com.portosoftware.chamados.ui.navigation.CallDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallDetailUiState(
    val call: UiState<CallDetail> = UiState.Loading,
    val canAttend: Boolean = false,
    val assuming: Boolean = false,
    val message: String? = null,
) {
    /** Status desconhecido não libera ação: sem "Atender" nem "Assumir". */
    val showsAttend: Boolean
        get() = canAttend && call.dataOrNull?.let { it.status != CallStatus.UNKNOWN } == true

    /** "Assumir" só aparece para chamado Aberto e sem responsável. */
    val canAssume: Boolean
        get() = canAttend && call.dataOrNull?.let { it.status == CallStatus.OPENED && it.isUnassigned } == true
}

@HiltViewModel
class CallDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val calls: CallRepository,
        private val auth: AuthRepository,
    ) : ViewModel() {
        private val id = savedStateHandle.toRoute<CallDetailRoute>().id

        private val _state =
            MutableStateFlow(
                CallDetailUiState(canAttend = auth.activeSession()?.let { RolePolicy(it.user.role).canAttend } ?: false),
            )
        val state: StateFlow<CallDetailUiState> = _state.asStateFlow()

        init {
            load()
            viewModelScope.launch { calls.changes.filter { it == id }.collect { load(silent = true) } }
        }

        fun load(silent: Boolean = false) {
            if (!silent) _state.update { it.copy(call = UiState.Loading) }
            viewModelScope.launch {
                val result = calls.detail(id).toUiState()
                _state.update {
                    // Atualização em segundo plano que falha não apaga o chamado que já está na tela.
                    if (silent && result is UiState.Error && it.call is UiState.Success) {
                        it.copy(message = result.error.message)
                    } else {
                        it.copy(call = result)
                    }
                }
            }
        }

        /** Atalho: assume como responsável e muda para Em atendimento, com nota automática. */
        fun assume() {
            val user = auth.activeSession()?.user ?: return
            if (_state.value.assuming) return
            _state.update { it.copy(assuming = true) }
            viewModelScope.launch {
                val result =
                    calls.registerProgress(
                        id,
                        ProgressRequest(CallRules.assumeNote(user.name), CallStatus.IN_PROGRESS, user.id, emptyList()),
                    )
                _state.update {
                    when (result) {
                        is AppResult.Success -> {
                            it.copy(
                                assuming = false,
                                call = UiState.Success(result.value),
                                message = "Você assumiu o chamado.",
                            )
                        }

                        is AppResult.Failure -> {
                            it.copy(assuming = false, message = result.error.message)
                        }
                    }
                }
                // Outro técnico pode ter assumido antes: mostra o estado atual em vez do antigo.
                if (result is AppResult.Failure) load(silent = true)
            }
        }

        fun onProgressSaved() = _state.update { it.copy(message = "Atendimento registrado.") }

        fun showMessage(text: String) = _state.update { it.copy(message = text) }

        fun messageShown() = _state.update { it.copy(message = null) }
    }
