package br.com.portosoftware.chamados.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.FiberNew
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.portosoftware.chamados.core.model.CallDashboard
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.ui.common.AvatarButton
import br.com.portosoftware.chamados.ui.common.CallCard
import br.com.portosoftware.chamados.ui.common.CallCardSkeleton
import br.com.portosoftware.chamados.ui.common.ErrorState
import br.com.portosoftware.chamados.ui.common.FullScreenState
import br.com.portosoftware.chamados.ui.common.KpiCard
import br.com.portosoftware.chamados.ui.common.SectionHeader
import br.com.portosoftware.chamados.ui.common.SkeletonBox
import br.com.portosoftware.chamados.ui.common.StateMessage
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.firstName
import br.com.portosoftware.chamados.ui.navigation.CallListPreset
import br.com.portosoftware.chamados.ui.preview.PreviewData
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme
import br.com.portosoftware.chamados.ui.theme.Spacing

@Composable
fun HomeRoute(
    onOpenCall: (String) -> Unit,
    onOpenList: (CallListPreset?) -> Unit,
    onNewCall: () -> Unit,
    onOpenProfile: (() -> Unit)?,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onRetry = { viewModel.load() },
        onRefresh = viewModel::refresh,
        onOpenCall = onOpenCall,
        onOpenList = onOpenList,
        onNewCall = onNewCall,
        onSwitchSecretary = viewModel::switchSecretary,
        onOpenProfile = onOpenProfile,
        onMessageConsume = viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCall: (String) -> Unit,
    onOpenList: (CallListPreset?) -> Unit,
    onNewCall: () -> Unit,
    onSwitchSecretary: (Secretary) -> Unit,
    onOpenProfile: (() -> Unit)?,
    onMessageConsume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val currentOnMessageConsume by rememberUpdatedState(onMessageConsume)
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            currentOnMessageConsume()
        }
    }
    var secretarySheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Olá, ${state.userName.firstName()}", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    state.secretary?.let { secretary ->
                        val canSwitch = state.secretaries.size > 1
                        // Com uma secretaria só o chip não abre nada, mas continua sendo informação:
                        // as cores de "desabilitado" (38% de opacidade) deixariam o nome ilegível.
                        val readOnlyColors =
                            AssistChipDefaults.assistChipColors(
                                disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                                disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary,
                            )
                        AssistChip(
                            onClick = { secretarySheet = true },
                            label = { Text(secretary.name.removePrefix("Secretaria de ").removePrefix("Secretaria ")) },
                            leadingIcon = { Icon(Icons.Outlined.Business, null, Modifier.size(18.dp)) },
                            trailingIcon = if (canSwitch) ({ Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) }) else null,
                            enabled = canSwitch,
                            colors = if (canSwitch) AssistChipDefaults.assistChipColors() else readOnlyColors,
                            border = AssistChipDefaults.assistChipBorder(enabled = true),
                            // As ações da TopAppBar têm só 4 dp de margem: completa os 16 dp da tela.
                            modifier = Modifier.padding(end = if (onOpenProfile == null) Spacing.md else Spacing.xs),
                        )
                    }
                    if (onOpenProfile != null) AvatarButton(state.userName, onOpenProfile)
                },
            )
        },
        floatingActionButton = {
            if (state.canCreateCall) {
                ExtendedFloatingActionButton(
                    onClick = onNewCall,
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Novo chamado") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val d = state.dashboard) {
                UiState.Loading -> {
                    HomeSkeleton()
                }

                is UiState.Error -> {
                    FullScreenState { ErrorState(d.error, onRetry) }
                }

                UiState.Empty -> {
                    FullScreenState {
                        StateMessage(
                            Icons.Outlined.Inbox,
                            "Nenhum chamado ativo",
                            body = "A fila desta secretaria está zerada. Novos chamados aparecem aqui.",
                            actionLabel = if (state.canCreateCall) "Abrir chamado" else null,
                            onAction = onNewCall,
                        )
                    }
                }

                is UiState.Success -> {
                    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        DashboardContent(d.data, listState, onOpenCall, onOpenList)
                    }
                }
            }
        }
    }

    if (secretarySheet) {
        ModalBottomSheet(onDismissRequest = { secretarySheet = false }) {
            Text(
                "Trocar secretaria",
                style = MaterialTheme.typography.titleMedium,
                // Mesmo recuo do texto dos ListItems abaixo.
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            // Rola com muitas secretarias (o admin pode ter as 11) ou em paisagem.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl)) {
                state.secretaries.forEach { secretary ->
                    ListItem(
                        headlineContent = { Text(secretary.name) },
                        trailingContent = {
                            if (secretary.id == state.secretary?.id) Icon(Icons.Outlined.Check, contentDescription = "Atual")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier =
                            Modifier.clickable {
                                secretarySheet = false
                                onSwitchSecretary(secretary)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    data: CallDashboard,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenCall: (String) -> Unit,
    onOpenList: (CallListPreset?) -> Unit,
) {
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
        item {
            // Carrossel com alertas primeiro; o próximo card aparece cortado na borda.
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    KpiCard(
                        "Urgentes",
                        data.urgent,
                        Icons.Outlined.PriorityHigh,
                        highlight = true,
                        onClick = { onOpenList(CallListPreset.URGENT) },
                    )
                }
                item {
                    KpiCard("Sem responsável", data.unassigned, Icons.Outlined.PersonOff, highlight = true, onClick = {
                        onOpenList(CallListPreset.UNASSIGNED)
                    })
                }
                item {
                    KpiCard(
                        "Ativos",
                        data.totalActive,
                        Icons.Outlined.ConfirmationNumber,
                        highlight = false,
                        onClick = { onOpenList(CallListPreset.ACTIVE) },
                    )
                }
                item {
                    KpiCard(
                        "Novos",
                        data.newCalls,
                        Icons.Outlined.FiberNew,
                        highlight = false,
                        onClick = { onOpenList(CallListPreset.NEW) },
                    )
                }
                item {
                    KpiCard(
                        "Em andamento",
                        data.inProgress,
                        Icons.Outlined.Build,
                        highlight = false,
                        onClick = { onOpenList(CallListPreset.IN_PROGRESS) },
                    )
                }
            }
        }
        item {
            SectionHeader("Recentes", modifier = Modifier.padding(top = Spacing.sm)) {
                TextButton(onClick = { onOpenList(null) }) { Text("Ver todos") }
            }
        }
        items(data.recentCalls, key = { it.id }) { call ->
            CallCard(
                call,
                onClick = { onOpenCall(call.id) },
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(3) { SkeletonBox(width = 136.dp, height = 112.dp) }
        }
        SkeletonBox(width = 120.dp, height = 20.dp)
        repeat(3) { CallCardSkeleton() }
    }
}

// --- Previews ------------------------------------------------------------------------------------

private val previewState =
    HomeUiState(
        userName = PreviewData.user.name,
        secretary = PreviewData.secretary,
        secretaries = PreviewData.user.secretaries,
        canCreateCall = true,
        dashboard = UiState.Success(PreviewData.dashboard),
    )

@Composable
private fun HomePreview(state: HomeUiState) =
    ChamadosTheme {
        HomeScreen(state, {}, {}, {}, {}, {}, {}, null, {})
    }

@PreviewLightDark
@Composable
private fun HomeSuccessPreview() = HomePreview(previewState)

@Preview(name = "Carregando")
@Composable
private fun HomeLoadingPreview() = HomePreview(previewState.copy(dashboard = UiState.Loading))

@Preview(name = "Vazio")
@Composable
private fun HomeEmptyPreview() = HomePreview(previewState.copy(dashboard = UiState.Empty))

@Preview(name = "Erro")
@Composable
private fun HomeErrorPreview() = HomePreview(previewState.copy(dashboard = UiState.Error(DomainError.Network())))
