package br.com.portosoftware.chamados.ui.calls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.data.CatalogRepository
import br.com.portosoftware.chamados.core.data.activeSession
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.RolePolicy
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.ui.common.SEARCH_DEBOUNCE_MS
import br.com.portosoftware.chamados.ui.navigation.CallListPreset
import br.com.portosoftware.chamados.ui.navigation.CallsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallListUiState(
    val userName: String = "",
    val query: String = "",
    val filter: CallFilter = CallFilter(),
    val canCreateCall: Boolean = false,
    val categories: List<Category> = emptyList(),
    val categoriesNotLoaded: Long = 0,
    val units: List<OrgUnit> = emptyList(),
    val technicians: List<Technician> = emptyList(),
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CallListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: CallRepository,
        private val catalog: CatalogRepository,
        auth: AuthRepository,
    ) : ViewModel() {
        private val secretaryId = auth.activeSession()?.secretary?.id
        private val canListTechnicians = auth.activeSession()?.let { RolePolicy(it.user.role).canListTechnicians } ?: false

        private val initialFilter = presetFilter(savedStateHandle.toRoute<CallsRoute>().preset)

        private val _state =
            MutableStateFlow(
                CallListUiState(
                    userName =
                        auth
                            .activeSession()
                            ?.user
                            ?.name
                            .orEmpty(),
                    filter = initialFilter,
                    canCreateCall = auth.activeSession()?.let { RolePolicy(it.user.role).canCreateCall } ?: false,
                ),
            )
        val state: StateFlow<CallListUiState> = _state.asStateFlow()

        private val filter = MutableStateFlow(initialFilter)

        /**
         * Incrementado quando um chamado muda (Atender, Novo chamado). Coletado no viewModelScope,
         * e não na tela, para não perder eventos enquanto a lista está fora da composição.
         */
        private val invalidations = MutableStateFlow(0)

        val calls: Flow<PagingData<CallSummary>> =
            combine(filter, invalidations) { f, _ -> f }
                .flatMapLatest { f ->
                    Pager(PagingConfig(pageSize = CallPagingSource.PAGE_SIZE, initialLoadSize = CallPagingSource.PAGE_SIZE)) {
                        CallPagingSource(repository, f)
                    }.flow
                }.cachedIn(viewModelScope)

        init {
            viewModelScope.launch { repository.changes.collect { invalidations.update { count -> count + 1 } } }
            viewModelScope.launch {
                // Compara com a busca aplicada, não com a última emitida: limpar os filtros e redigitar
                // o mesmo texto antes do debounce ficaria sem efeito.
                _state.drop(1).debounce(SEARCH_DEBOUNCE_MS).filter { it.query != it.filter.search }.collect { s ->
                    applyFilter(s.filter.copy(search = s.query))
                }
            }
            viewModelScope.launch {
                (catalog.categories() as? AppResult.Success)?.let { r ->
                    _state.update { it.copy(categories = r.value.items, categoriesNotLoaded = r.value.notLoaded) }
                }
                if (canListTechnicians) {
                    (catalog.technicians() as? AppResult.Success)?.let { r -> _state.update { it.copy(technicians = r.value) } }
                }
                secretaryId?.let { id ->
                    (catalog.units(id) as? AppResult.Success)?.let { r -> _state.update { it.copy(units = r.value) } }
                }
            }
        }

        fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

        fun setStatus(status: CallStatus?) = applyFilter(_state.value.filter.copy(statuses = setOfNotNull(status)))

        fun setPriority(priority: CallPriority?) = applyFilter(_state.value.filter.copy(priority = priority))

        fun toggleUnassigned() = applyFilter(_state.value.filter.let { it.copy(unassigned = !it.unassigned) })

        fun setCategory(id: String?) = applyFilter(_state.value.filter.copy(categoryId = id))

        fun setTechnician(id: String?) = applyFilter(_state.value.filter.copy(technicianId = id))

        fun setUnit(id: String?) = applyFilter(_state.value.filter.copy(unitId = id))

        fun clearFilters() {
            _state.update { it.copy(query = "") }
            applyFilter(CallFilter())
        }

        private fun applyFilter(newFilter: CallFilter) {
            val f = newFilter.copy(search = _state.value.query)
            _state.update { it.copy(filter = f) }
            filter.value = f
        }

        companion object {
            fun presetFilter(preset: String?): CallFilter =
                when (preset?.let { runCatching { CallListPreset.valueOf(it) }.getOrNull() }) {
                    // Mesmos recortes dos cards do Início: urgentes e sem responsável só contam os ativos.
                    CallListPreset.ACTIVE -> CallFilter(statuses = CallRules.activeStatuses)

                    CallListPreset.NEW -> CallFilter(statuses = setOf(CallStatus.OPENED))

                    CallListPreset.IN_PROGRESS -> CallFilter(statuses = setOf(CallStatus.IN_PROGRESS))

                    CallListPreset.URGENT -> CallFilter(statuses = CallRules.activeStatuses, priority = CallPriority.URGENT)

                    CallListPreset.UNASSIGNED -> CallFilter(statuses = CallRules.activeStatuses, unassigned = true)

                    null -> CallFilter()
                }
        }
    }
