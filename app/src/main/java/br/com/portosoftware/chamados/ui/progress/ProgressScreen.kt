package br.com.portosoftware.chamados.ui.progress

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.ui.common.Banner
import br.com.portosoftware.chamados.ui.common.ErrorState
import br.com.portosoftware.chamados.ui.common.FullScreenState
import br.com.portosoftware.chamados.ui.common.QuantityStepper
import br.com.portosoftware.chamados.ui.common.SelectField
import br.com.portosoftware.chamados.ui.common.SkeletonBox
import br.com.portosoftware.chamados.ui.common.StateMessage
import br.com.portosoftware.chamados.ui.common.StatusChip
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.icon
import br.com.portosoftware.chamados.ui.common.itemsNotLoadedNotice
import br.com.portosoftware.chamados.ui.common.protocolOf
import br.com.portosoftware.chamados.ui.preview.PreviewData
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme
import br.com.portosoftware.chamados.ui.theme.Spacing
import kotlinx.coroutines.flow.first

@Composable
fun ProgressRoute(
    onClose: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSaveSuccess by rememberUpdatedState(onSaveSuccess)
    LaunchedEffect(viewModel) {
        // Navega uma única vez, mesmo que "saved" continue verdadeiro em recomposições.
        viewModel.state.first { it.saved }
        currentOnSaveSuccess()
    }
    ProgressScreen(
        state = state,
        actions =
            ProgressActions(
                onClose = onClose,
                onRetryLoad = viewModel::load,
                onNote = viewModel::onNoteChange,
                onStatus = viewModel::onStatus,
                onTechnician = viewModel::onTechnician,
                onSearchStock = viewModel::searchStock,
                onAddPart = viewModel::addPart,
                onQuantity = viewModel::setQuantity,
                onRemovePart = viewModel::removePart,
                onSubmit = viewModel::submit,
            ),
    )
}

data class ProgressActions(
    val onClose: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onStatus: (CallStatus) -> Unit = {},
    val onTechnician: (String?) -> Unit = {},
    val onSearchStock: (String) -> Unit = {},
    val onAddPart: (StockItem) -> Unit = {},
    val onQuantity: (String, Int) -> Unit = { _, _ -> },
    val onRemovePart: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
)

/** Atender: diálogo de tela cheia para registrar o que foi feito, status e peças. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    state: ProgressUiState,
    actions: ProgressActions,
    modifier: Modifier = Modifier,
    initialSheetOpen: Boolean = false,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var partsSheet by rememberSaveable { mutableStateOf(initialSheetOpen) }
    val close = { if (state.hasDraft) confirmDiscard = true else actions.onClose() }
    BackHandler(enabled = !state.submitting, onBack = close)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Atender " + (state.call as? UiState.Success)?.data?.let { protocolOf(it.id) }.orEmpty()) },
                navigationIcon = {
                    IconButton(
                        onClick = close,
                        enabled = !state.submitting,
                    ) { Icon(Icons.Outlined.Close, contentDescription = "Fechar") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = actions.onSubmit,
                    enabled = state.canSubmit,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(Spacing.lg),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Text("  Registrando…")
                    } else {
                        Text("Registrar atendimento")
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val call = state.call) {
                UiState.Loading -> {
                    Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SkeletonBox(height = 120.dp)
                        SkeletonBox(height = 48.dp)
                        SkeletonBox(height = 48.dp)
                    }
                }

                is UiState.Error -> {
                    FullScreenState { ErrorState(call.error, actions.onRetryLoad) }
                }

                UiState.Empty -> {}

                is UiState.Success -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        state.submitError?.let { error ->
                            Banner(
                                "Não foi possível registrar. ${error.message}",
                                actionLabel = "Tentar de novo",
                                onAction = actions.onSubmit,
                            )
                        }
                        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(
                                    "Status atual",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                StatusChip(call.data.status)
                            }
                            OutlinedTextField(
                                value = state.note,
                                onValueChange = actions.onNote,
                                label = { Text("O que foi feito *") },
                                isError = state.noteError != null,
                                supportingText = {
                                    Row {
                                        Text(state.noteError ?: "Fica no histórico do chamado.", modifier = Modifier.weight(1f))
                                        Text("${state.note.length}/${CallRules.NOTE_MAX}")
                                    }
                                },
                                enabled = !state.submitting,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                            )
                            Text("Novo status", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                state.statusOptions.forEach { option ->
                                    FilterChip(
                                        selected = state.status == option,
                                        onClick = { actions.onStatus(option) },
                                        enabled = !state.submitting,
                                        leadingIcon = { Icon(option.icon, null, Modifier.size(18.dp)) },
                                        label = { Text(if (option == call.data.status) "${option.label} (atual)" else option.label) },
                                    )
                                }
                            }
                            SelectField(
                                label = "Responsável",
                                options = state.technicians,
                                selected = state.technicians.firstOrNull { it.id == state.technicianId },
                                optionLabel = { it.name },
                                onSelect = { actions.onTechnician(it?.id) },
                                enabled = !state.submitting,
                            )
                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Peças utilizadas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        partsSheet = true
                                        actions.onSearchStock("")
                                    },
                                    enabled =
                                        !state.submitting && state.canUseParts,
                                ) {
                                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                    Text(" Adicionar peça")
                                }
                            }
                            if (!state.canUseParts) {
                                Text(
                                    "Este chamado não tem equipamento vinculado. " +
                                        "Para usar peças do estoque, vincule o equipamento no sistema web.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (state.parts.isEmpty()) {
                                Text(
                                    "Nenhuma peça adicionada. Se usou material do estoque, adicione aqui para dar baixa.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.parts.any { it.item.isUnique }) {
                                Text(
                                    "Componentes únicos substituem o atual, que vai para manutenção.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            state.parts.forEach { line ->
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(start = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(line.item.model, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                listOf("Saldo: ${line.item.quantity}", line.item.summary)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        QuantityStepper(
                                            line.quantity,
                                            line.item.maxPerCall,
                                            { actions.onQuantity(line.item.id, it) },
                                            line.item.model,
                                        )
                                        IconButton(onClick = { actions.onRemovePart(line.item.id) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Remover ${line.item.model}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (partsSheet) {
        PartsSheet(state, actions, onDismiss = { partsSheet = false })
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Descartar atendimento?") },
            text = { Text("O que você escreveu e as peças adicionadas serão perdidos.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    actions.onClose()
                }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Continuar editando") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartsSheet(
    state: ProgressUiState,
    actions: ProgressActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Sem recuo na coluna: os ListItems ocupam a largura toda e já recuam o texto em 16 dp,
        // alinhado com o título e a busca.
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Adicionar peça",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md),
            )
            OutlinedTextField(
                value = state.stockQuery,
                onValueChange = actions.onSearchStock,
                placeholder = { Text("Buscar no estoque", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            )
            if (state.stockNotLoaded > 0) {
                Text(
                    itemsNotLoadedNotice(state.stockNotLoaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                )
            }
            Box(Modifier.heightIn(min = 240.dp, max = 480.dp)) {
                when (val stock = state.stock) {
                    UiState.Loading -> {
                        Box(
                            Modifier.fillMaxWidth().padding(Spacing.xxl),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }

                    UiState.Empty -> {
                        StateMessage(Icons.Outlined.Inventory2, "Nada encontrado", body = "Nenhum item em estoque com esse nome.")
                    }

                    is UiState.Error -> {
                        ErrorState(stock.error, onRetry = { actions.onSearchStock(state.stockQuery) })
                    }

                    is UiState.Success -> {
                        LazyColumn {
                            items(stock.data, key = { it.id }) { item ->
                                val added = state.parts.any { it.item.id == item.id }
                                val available = item.quantity > 0 && !added
                                ListItem(
                                    headlineContent = { Text(item.model) },
                                    supportingContent = {
                                        Text(
                                            listOf(
                                                when {
                                                    added -> "Já adicionada"
                                                    item.quantity <= 0 -> "Sem saldo"
                                                    else -> "Saldo: ${item.quantity}"
                                                },
                                                item.summary,
                                            ).filter { it.isNotBlank() }.joinToString(" · "),
                                        )
                                    },
                                    trailingContent = {
                                        OutlinedButton(onClick = {
                                            actions.onAddPart(item)
                                            onDismiss()
                                        }, enabled = available) { Text("Adicionar") }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Previews ------------------------------------------------------------------------------------

private val previewForm =
    ProgressUiState(
        call = UiState.Success(PreviewData.detail),
        note = "Troquei a memória e reinstalei o cliente do sistema.",
        status = CallStatus.CLOSED,
        statusOptions = CallRules.progressOptions(CallStatus.IN_PROGRESS),
        technicianId = PreviewData.technician.id,
        technicians = listOf(PreviewData.technician),
        parts = listOf(PartLine(PreviewData.stock[0], 1)),
        stock = UiState.Success(PreviewData.stock),
    )

@PreviewLightDark
@Composable
private fun ProgressFormPreview() = ChamadosTheme { ProgressScreen(previewForm, ProgressActions()) }

@Preview(name = "Sem peças")
@Composable
private fun ProgressNoPartsPreview() = ChamadosTheme { ProgressScreen(previewForm.copy(parts = emptyList(), note = ""), ProgressActions()) }

@Preview(name = "Erro de envio + validação")
@Composable
private fun ProgressErrorPreview() =
    ChamadosTheme {
        ProgressScreen(
            previewForm.copy(note = "", noteError = "Descreva o que foi feito.", submitError = DomainError.Network()),
            ProgressActions(),
        )
    }

@Preview(name = "Enviando")
@Composable
private fun ProgressSubmittingPreview() = ChamadosTheme { ProgressScreen(previewForm.copy(submitting = true), ProgressActions()) }

@Preview(name = "Sheet Adicionar peça")
@Composable
private fun ProgressSheetPreview() = ChamadosTheme { ProgressScreen(previewForm, ProgressActions(), initialSheetOpen = true) }
