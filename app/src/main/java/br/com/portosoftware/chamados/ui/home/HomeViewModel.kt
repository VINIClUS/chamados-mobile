package br.com.portosoftware.chamados.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.data.activeSession
import br.com.portosoftware.chamados.core.model.CallDashboard
import br.com.portosoftware.chamados.core.model.RolePolicy
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.toUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val secretary: Secretary? = null,
    val secretaries: List<Secretary> = emptyList(),
    val canCreateCall: Boolean = false,
    val dashboard: UiState<CallDashboard> = UiState.Loading,
    val refreshing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val calls: CallRepository,
        private val auth: AuthRepository,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                auth.activeSession().let { s ->
                    HomeUiState(
                        userName = s?.user?.name.orEmpty(),
                        secretary = s?.secretary,
                        secretaries = s?.user?.secretaries.orEmpty(),
                        canCreateCall = s?.let { RolePolicy(it.user.role).canCreateCall } ?: false,
                    )
                },
            )
        val state: StateFlow<HomeUiState> = _state.asStateFlow()

        init {
            load()
            viewModelScope.launch { calls.changes.collect { load(silent = true) } }
        }

        fun load(silent: Boolean = false) {
            if (!silent) _state.update { it.copy(dashboard = UiState.Loading) }
            viewModelScope.launch { fetch() }
        }

        fun refresh() {
            _state.update { it.copy(refreshing = true) }
            viewModelScope.launch { fetch() }
        }

        private suspend fun fetch() {
            val result = calls.dashboard().toUiState { it.totalActive == 0L && it.recentCalls.isEmpty() }
            _state.update {
                // Falha ao atualizar mantém o painel que já está na tela e avisa.
                if (result is UiState.Error && it.dashboard is UiState.Success) {
                    it.copy(refreshing = false, message = result.error.message)
                } else {
                    it.copy(dashboard = result, refreshing = false)
                }
            }
        }

        fun messageShown() = _state.update { it.copy(message = null) }

        fun switchSecretary(secretary: Secretary) {
            if (secretary.id == _state.value.secretary?.id) return
            viewModelScope.launch { auth.selectSecretary(secretary) }
        }
    }
