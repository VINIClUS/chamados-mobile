package br.com.portosoftware.chamados.ui.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.DomainException
import br.com.portosoftware.chamados.ui.common.AvatarButton
import br.com.portosoftware.chamados.ui.common.Banner
import br.com.portosoftware.chamados.ui.common.CallCard
import br.com.portosoftware.chamados.ui.common.CallCardSkeleton
import br.com.portosoftware.chamados.ui.common.ErrorState
import br.com.portosoftware.chamados.ui.common.FullScreenState
import br.com.portosoftware.chamados.ui.common.StateMessage
import br.com.portosoftware.chamados.ui.common.categoriesNotLoadedNotice
import br.com.portosoftware.chamados.ui.preview.PreviewData
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme
import br.com.portosoftware.chamados.ui.theme.Spacing
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

@Composable
fun CallListRoute(
    onOpenCall: (String) -> Unit,
    onNewCall: () -> Unit,
    onOpenProfile: (() -> Unit)?,
    viewModel: CallListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = viewModel.calls.collectAsLazyPagingItems()
    CallListScreen(
        state = state,
        items = items,
        actions =
            CallListActions(
                onQueryChange = viewModel::onQueryChange,
                onStatus = viewModel::setStatus,
                onPriority = viewModel::setPriority,
                onToggleUnassigned = viewModel::toggleUnassigned,
                onCategory = viewModel::setCategory,
                onTechnician = viewModel::setTechnician,
                onUnit = viewModel::setUnit,
                onClearFilters = viewModel::clearFilters,
                onOpenCall = onOpenCall,
                onNewCall = onNewCall,
                onOpenProfile = onOpenProfile,
            ),
    )
}

data class CallListActions(
    val onQueryChange: (String) -> Unit = {},
    val onStatus: (CallStatus?) -> Unit = {},
    val onPriority: (CallPriority?) -> Unit = {},
    val onToggleUnassigned: () -> Unit = {},
    val onCategory: (String?) -> Unit = {},
    val onTechnician: (String?) -> Unit = {},
    val onUnit: (String?) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onOpenCall: (String) -> Unit = {},
    val onNewCall: () -> Unit = {},
    val onOpenProfile: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallListScreen(
    state: CallListUiState,
    items: LazyPagingItems<CallSummary>,
    actions: CallListActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var moreFilters by rememberSaveable { mutableStateOf(false) }
    val refresh = items.loadState.refresh

    // Filtro ou busca nova abre a lista no topo. A LazyColumn ancora pela chave do primeiro item
    // visível: sem isso, ao limpar uma busca a lista reabria no meio, com os chamados de cima
    // escondidos. Espera a carga do novo filtro terminar, senão a âncora vence de novo; recargas
    // com o mesmo filtro (depois de Atender ou Novo chamado) mantêm a posição.
    val currentFilter by rememberUpdatedState(state.filter)
    LaunchedEffect(listState, items) {
        snapshotFlow { currentFilter }.drop(1).collectLatest { _ ->
            snapshotFlow { items.loadState.refresh }
                .dropWhile { it !is LoadState.Loading }
                .first { it is LoadState.NotLoading }
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Chamados") },
                actions = { actions.onOpenProfile?.let { AvatarButton(state.userName, it) } },
            )
        },
        floatingActionButton = {
            if (state.canCreateCall) {
                ExtendedFloatingActionButton(
                    onClick = actions.onNewCall,
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Novo chamado") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                placeholder = { Text("Buscar por descrição, protocolo ou solicitante", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = { actions.onQueryChange("") },
                        ) { Icon(Icons.Outlined.Close, contentDescription = "Limpar busca") }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            )
            QuickFilters(state.filter, actions, onMore = { moreFilters = true })

            // Falha ao atualizar com dados já em tela: mantém a lista e avisa no topo.
            if (refresh is LoadState.Error && items.itemCount > 0) {
                Banner("Sem conexão. Mostrando a última lista carregada.", actionLabel = "Tentar", onAction = { items.refresh() })
            }

            Box(Modifier.weight(1f)) {
                when {
                    refresh is LoadState.Loading && items.itemCount == 0 -> {
                        ListSkeleton()
                    }

                    refresh is LoadState.Error && items.itemCount == 0 -> {
                        FullScreenState {
                            ErrorState((refresh.error as? DomainException)?.error ?: DomainError.Unknown(), onRetry = { items.refresh() })
                        }
                    }

                    refresh is LoadState.NotLoading && items.itemCount == 0 -> {
                        FullScreenState {
                            val filtered = state.filter.activeCount > 0 || state.query.isNotBlank()
                            StateMessage(
                                Icons.Outlined.SearchOff,
                                if (filtered) "Nenhum chamado com esses filtros" else "Nenhum chamado por aqui",
                                body =
                                    if (filtered) {
                                        "Tente outros termos ou limpe os filtros."
                                    } else {
                                        "Quando houver chamados nesta secretaria, eles aparecem aqui."
                                    },
                                actionLabel = if (filtered) "Limpar filtros" else null,
                                onAction = actions.onClearFilters,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                                items[index]?.let { call -> CallCard(call, onClick = { actions.onOpenCall(call.id) }) }
                            }
                            item { AppendFooter(items) }
                        }
                    }
                }
            }
        }
    }

    if (moreFilters) {
        MoreFiltersSheet(state, actions, onDismiss = { moreFilters = false })
    }
}

@Composable
private fun QuickFilters(
    filter: CallFilter,
    actions: CallListActions,
    onMore: () -> Unit,
) {
    var statusMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Box {
                FilterChip(
                    selected = filter.statuses.isNotEmpty(),
                    onClick = { statusMenu = true },
                    label = { Text(filter.statuses.chipLabel()) },
                    trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(statusMenu, onDismissRequest = { statusMenu = false }) {
                    DropdownMenuItem(text = { Text("Todos") }, onClick = {
                        statusMenu = false
                        actions.onStatus(null)
                    })
                    CallRules.filterableStatuses.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = {
                            statusMenu = false
                            actions.onStatus(s)
                        })
                    }
                }
            }
        }
        item {
            Box {
                FilterChip(
                    selected = filter.priority != null,
                    onClick = { priorityMenu = true },
                    label = { Text(filter.priority?.let { "Prioridade ${it.label.lowercase()}" } ?: "Prioridade") },
                    trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(priorityMenu, onDismissRequest = { priorityMenu = false }) {
                    DropdownMenuItem(text = { Text("Todas") }, onClick = {
                        priorityMenu = false
                        actions.onPriority(null)
                    })
                    CallPriority.selectable.reversed().forEach { p ->
                        DropdownMenuItem(text = { Text(p.label) }, onClick = {
                            priorityMenu = false
                            actions.onPriority(p)
                        })
                    }
                }
            }
        }
        item { FilterChip(selected = filter.unassigned, onClick = actions.onToggleUnassigned, label = { Text("Sem responsável") }) }
        item {
            val extra = listOf(filter.categoryId, filter.technicianId, filter.unitId).count { it != null }
            FilterChip(
                selected = extra > 0,
                onClick = onMore,
                label = { Text("Mais filtros") },
                leadingIcon = { Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp)) },
                // O contador vai à direita: no canto do ícone de 18 dp ele cobria o ícone inteiro.
                trailingIcon = if (extra > 0) ({ Badge { Text("$extra") } }) else null,
            )
        }
    }
}

/** "Ativos" é o recorte dos atalhos do Início; o menu de status escolhe um por vez. */
private fun Set<CallStatus>.chipLabel(): String =
    when {
        isEmpty() -> "Status"
        this == CallRules.activeStatuses -> "Ativos"
        else -> joinToString { it.label }
    }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MoreFiltersSheet(
    state: CallListUiState,
    actions: CallListActions,
    onDismiss: () -> Unit,
) {
    // Abre inteiro: meio aberto, "Ver resultados" e "Limpar tudo" ficavam abaixo da dobra.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            "Mais filtros",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.xxl).padding(bottom = Spacing.md),
        )
        // Só as opções rolam: a Saúde tem ~25 unidades, e sem rolagem Categoria, Técnico e os
        // botões ficavam fora da tela. Os botões ficam fixos no rodapé.
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.units.size > 1) {
                Text("Unidade", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    state.units.forEach { u ->
                        val selected = state.filter.unitId == u.id
                        FilterChip(selected, onClick = { actions.onUnit(if (selected) null else u.id) }, label = { Text(u.name) })
                    }
                }
            }
            Text("Categoria", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.categories.forEach { c ->
                    val selected = state.filter.categoryId == c.id
                    FilterChip(selected, onClick = { actions.onCategory(if (selected) null else c.id) }, label = { Text(c.name) })
                }
            }
            if (state.categoriesNotLoaded > 0) {
                Text(
                    categoriesNotLoadedNotice(state.categoriesNotLoaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.technicians.isNotEmpty()) {
                Text("Técnico responsável", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    state.technicians.forEach { t ->
                        val selected = state.filter.technicianId == t.id
                        FilterChip(selected, onClick = { actions.onTechnician(if (selected) null else t.id) }, label = { Text(t.name) })
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(horizontal = Spacing.xxl).padding(top = Spacing.lg, bottom = Spacing.xxl),
        ) {
            OutlinedButton(onClick = {
                actions.onClearFilters()
                onDismiss()
            }, modifier = Modifier.weight(1f)) { Text("Limpar tudo") }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Ver resultados") }
        }
    }
}

@Composable
private fun AppendFooter(items: LazyPagingItems<CallSummary>) {
    when (val append = items.loadState.append) {
        LoadState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }

        is LoadState.Error -> {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (append.error as? DomainException)?.error?.message ?: "Falha ao carregar mais.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { items.retry() }) { Text("Tentar novamente") }
            }
        }

        is LoadState.NotLoading -> {}
    }
}

@Composable
private fun ListSkeleton() {
    Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(4) { CallCardSkeleton() }
    }
}

// --- Previews ------------------------------------------------------------------------------------

private fun loadStates(
    refresh: LoadState,
    append: LoadState = LoadState.NotLoading(false),
) = LoadStates(refresh = refresh, prepend = LoadState.NotLoading(true), append = append)

@Composable
private fun CallListPreview(
    data: PagingData<CallSummary>,
    state: CallListUiState = CallListUiState(canCreateCall = true),
) {
    val items = flowOf(data).collectAsLazyPagingItems()
    ChamadosTheme { CallListScreen(state, items, CallListActions()) }
}

@PreviewLightDark
@Composable
private fun CallListSuccessPreview() =
    CallListPreview(
        PagingData.from(PreviewData.calls, loadStates(LoadState.NotLoading(false), LoadState.NotLoading(true))),
    )

@Preview(name = "Carregando")
@Composable
private fun CallListLoadingPreview() = CallListPreview(PagingData.empty(loadStates(LoadState.Loading)))

@Preview(name = "Vazio com filtros")
@Composable
private fun CallListEmptyPreview() =
    CallListPreview(
        PagingData.empty(loadStates(LoadState.NotLoading(true))),
        CallListUiState(query = "scanner", filter = CallFilter(priority = CallPriority.URGENT)),
    )

@Preview(name = "Erro")
@Composable
private fun CallListErrorPreview() =
    CallListPreview(
        PagingData.empty(loadStates(LoadState.Error(DomainException(DomainError.Network())))),
    )

@Preview(name = "Offline + falha de paginação")
@Composable
private fun CallListOfflinePreview() =
    CallListPreview(
        PagingData.from(
            PreviewData.calls,
            loadStates(LoadState.Error(DomainException(DomainError.Network())), LoadState.Error(DomainException(DomainError.Network()))),
        ),
    )
