package br.com.portosoftware.chamados.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.data.StockRepository
import br.com.portosoftware.chamados.core.data.activeSession
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.map
import br.com.portosoftware.chamados.ui.common.SEARCH_DEBOUNCE_MS
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.toUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockUiState(
    val userName: String = "",
    val query: String = "",
    val type: String? = null,
    val items: UiState<List<StockItem>> = UiState.Loading,
    val notLoaded: Long = 0,
) {
    val types: List<String> get() =
        (items as? UiState.Success)
            ?.data
            ?.mapNotNull { it.type }
            ?.distinct()
            ?.sorted()
            .orEmpty()

    val visible: List<StockItem>
        get() = (items as? UiState.Success)?.data.orEmpty().filter { type == null || it.type == type }
}

@HiltViewModel
class StockViewModel
    @Inject
    constructor(
        private val stock: StockRepository,
        calls: CallRepository,
        auth: AuthRepository,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                StockUiState(
                    userName =
                        auth
                            .activeSession()
                            ?.user
                            ?.name
                            .orEmpty(),
                ),
            )
        val state: StateFlow<StockUiState> = _state.asStateFlow()
        private var job: Job? = null

        init {
            load()
            // Um atendimento com peças baixa o saldo: a lista não pode ficar com o número antigo.
            viewModelScope.launch { calls.changes.collect { load() } }
        }

        fun onQueryChange(query: String) {
            _state.update { it.copy(query = query) }
            load(debounce = true)
        }

        fun onType(type: String?) = _state.update { it.copy(type = if (it.type == type) null else type) }

        fun load(debounce: Boolean = false) {
            job?.cancel()
            job =
                viewModelScope.launch {
                    if (debounce) delay(SEARCH_DEBOUNCE_MS)
                    _state.update { it.copy(items = UiState.Loading) }
                    val page = stock.items(_state.value.query)
                    val result = page.map { it.items }.toUiState { it.isEmpty() }
                    _state.update { s ->
                        val updated = s.copy(items = result, notLoaded = (page as? AppResult.Success)?.value?.notLoaded ?: 0)
                        // O tipo escolhido pode sumir da nova lista; sem isso a tela ficaria vazia sem chip marcado.
                        if (updated.type != null && result is UiState.Success && updated.type !in updated.types) {
                            updated.copy(type = null)
                        } else {
                            updated
                        }
                    }
                }
        }
    }
