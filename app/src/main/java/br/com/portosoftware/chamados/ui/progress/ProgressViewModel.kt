package br.com.portosoftware.chamados.ui.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.data.CatalogRepository
import br.com.portosoftware.chamados.core.data.StockRepository
import br.com.portosoftware.chamados.core.data.activeSession
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.core.model.UsedStockItem
import br.com.portosoftware.chamados.core.model.map
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.toUiState
import br.com.portosoftware.chamados.ui.navigation.ProgressRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartLine(
    val item: StockItem,
    val quantity: Int,
)

data class ProgressUiState(
    val call: UiState<CallDetail> = UiState.Loading,
    val note: String = "",
    val noteError: String? = null,
    val status: CallStatus? = null,
    val statusOptions: List<CallStatus> = emptyList(),
    val technicianId: String? = null,
    val technicians: List<Technician> = emptyList(),
    val parts: List<PartLine> = emptyList(),
    val stockQuery: String = "",
    val stock: UiState<List<StockItem>> = UiState.Loading,
    val stockNotLoaded: Long = 0,
    val submitting: Boolean = false,
    val submitError: DomainError? = null,
    val saved: Boolean = false,
) {
    val currentStatus: CallStatus? get() = (call as? UiState.Success)?.data?.status
    val hasDraft: Boolean get() = note.isNotBlank() || parts.isNotEmpty() || (status != null && status != currentStatus)
    val canSubmit: Boolean get() = call is UiState.Success && status != null && !submitting

    /** O backend só dá baixa em peças se o chamado tiver equipamento vinculado. */
    val canUseParts: Boolean get() = (call as? UiState.Success)?.data?.equipment != null
}

/** O seletor de peças busca enquanto o técnico digita, num ritmo um pouco mais curto. */
private const val STOCK_SEARCH_DEBOUNCE_MS = 300L

@OptIn(FlowPreview::class)
@HiltViewModel
class ProgressViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val calls: CallRepository,
        private val catalog: CatalogRepository,
        private val stock: StockRepository,
        auth: AuthRepository,
    ) : ViewModel() {
        private val callId = savedStateHandle.toRoute<ProgressRoute>().callId
        private val me = auth.activeSession()?.user

        private val _state = MutableStateFlow(ProgressUiState())
        val state: StateFlow<ProgressUiState> = _state.asStateFlow()
        private var stockJob: Job? = null

        init {
            load()
        }

        fun load() {
            _state.update { it.copy(call = UiState.Loading) }
            viewModelScope.launch {
                val result = calls.detail(callId)
                _state.update { s ->
                    val call = (result as? AppResult.Success)?.value
                    s.copy(
                        call = result.toUiState(),
                        statusOptions = call?.let { CallRules.progressOptions(it.status) }.orEmpty(),
                        status = s.status ?: call?.let { CallRules.progressOptions(it.status).firstOrNull() },
                        // Mantém o responsável atual; sem responsável, sugere quem está atendendo.
                        technicianId = s.technicianId ?: call?.technician?.id ?: me?.id,
                    )
                }
            }
            viewModelScope.launch {
                (catalog.technicians() as? AppResult.Success)?.let { r -> _state.update { it.copy(technicians = r.value) } }
            }
        }

        fun onNoteChange(value: String) = _state.update { it.copy(note = value.take(CallRules.NOTE_MAX), noteError = null) }

        fun onStatus(status: CallStatus) = _state.update { it.copy(status = status) }

        fun onTechnician(id: String?) = _state.update { it.copy(technicianId = id) }

        fun searchStock(query: String = _state.value.stockQuery) {
            _state.update { it.copy(stockQuery = query, stock = UiState.Loading) }
            stockJob?.cancel()
            stockJob =
                viewModelScope.launch {
                    delay(STOCK_SEARCH_DEBOUNCE_MS)
                    val page = stock.items(query)
                    val result = page.map { it.items }.toUiState { it.isEmpty() }
                    _state.update { it.copy(stock = result, stockNotLoaded = (page as? AppResult.Success)?.value?.notLoaded ?: 0) }
                }
        }

        fun addPart(item: StockItem) =
            _state.update { s ->
                if (s.parts.any { it.item.id == item.id } || s.parts.size >= CallRules.MAX_STOCK_LINES || item.quantity <= 0) {
                    s
                } else {
                    s.copy(parts = s.parts + PartLine(item, 1))
                }
            }

        fun setQuantity(
            itemId: String,
            quantity: Int,
        ) = _state.update { s ->
            s.copy(parts = s.parts.map { if (it.item.id == itemId) it.copy(quantity = quantity.coerceIn(1, it.item.maxPerCall)) else it })
        }

        private fun refreshParts() {
            viewModelScope.launch {
                val items = (stock.items("") as? AppResult.Success)?.value?.items ?: return@launch
                val byId = items.associateBy { it.id }
                _state.update { s ->
                    s.copy(
                        parts =
                            s.parts.mapNotNull { line ->
                                // A API devolve só a primeira página do estoque: peça fora dela fica como está.
                                val item = byId[line.item.id] ?: return@mapNotNull line
                                if (item.quantity <= 0) null else PartLine(item, line.quantity.coerceAtMost(item.maxPerCall))
                            },
                    )
                }
                if (_state.value.stock !is UiState.Loading) searchStock()
            }
        }

        fun removePart(itemId: String) = _state.update { s -> s.copy(parts = s.parts.filterNot { it.item.id == itemId }) }

        fun submit() {
            val s = _state.value
            val status = s.status
            if (!s.canSubmit || status == null || s.submitting) return
            if (s.note.isBlank()) {
                _state.update { it.copy(noteError = "Descreva o que foi feito.") }
                return
            }
            _state.update { it.copy(submitting = true, submitError = null) }
            viewModelScope.launch {
                val result =
                    calls.registerProgress(
                        callId,
                        ProgressRequest(
                            note = s.note,
                            status = status,
                            technicianId = s.technicianId,
                            stockItems = s.parts.map { UsedStockItem(it.item.id, it.quantity) },
                        ),
                    )
                _state.update {
                    when (result) {
                        is AppResult.Success -> {
                            it.copy(submitting = false, saved = true)
                        }

                        is AppResult.Failure -> {
                            it.copy(
                                submitting = false,
                                submitError = result.error,
                                noteError = (result.error as? DomainError.Validation)?.fieldErrors?.get("note"),
                            )
                        }
                    }
                }
                // Saldo pode ter mudado (ex.: "saldo insuficiente"): atualiza as peças mostradas.
                if (result is AppResult.Failure && s.parts.isNotEmpty()) refreshParts()
            }
        }
    }
