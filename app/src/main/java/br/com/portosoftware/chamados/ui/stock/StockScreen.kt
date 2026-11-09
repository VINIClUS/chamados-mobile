package br.com.portosoftware.chamados.ui.stock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.ui.common.AvatarButton
import br.com.portosoftware.chamados.ui.common.ErrorState
import br.com.portosoftware.chamados.ui.common.FullScreenState
import br.com.portosoftware.chamados.ui.common.SkeletonBox
import br.com.portosoftware.chamados.ui.common.StateMessage
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.itemsNotLoadedNotice
import br.com.portosoftware.chamados.ui.preview.PreviewData
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme
import br.com.portosoftware.chamados.ui.theme.Spacing

@Composable
fun StockRoute(
    onOpenProfile: (() -> Unit)?,
    viewModel: StockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StockScreen(state, viewModel::onQueryChange, viewModel::onType, { viewModel.load() }, onOpenProfile)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    state: StockUiState,
    onQueryChange: (String) -> Unit,
    onType: (String?) -> Unit,
    onRetry: () -> Unit,
    onOpenProfile: (() -> Unit)?,
    modifier: Modifier = Modifier,
    initialDetail: StockItem? = null,
) {
    var detailId by rememberSaveable { mutableStateOf(initialDetail?.id) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Estoque") },
                actions = { onOpenProfile?.let { AvatarButton(state.userName, it) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Buscar modelo, marca ou tipo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                        ) { Icon(Icons.Outlined.Close, "Limpar busca") }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            )
            if (state.types.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.types) { type -> FilterChip(state.type == type, onClick = { onType(type) }, label = { Text(type) }) }
                }
            }
            if (state.notLoaded > 0) {
                Text(
                    itemsNotLoadedNotice(state.notLoaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                )
            }
            Box(Modifier.weight(1f)) {
                when (val items = state.items) {
                    UiState.Loading -> {
                        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            repeat(6) { SkeletonBox(height = 56.dp) }
                        }
                    }

                    is UiState.Error -> {
                        FullScreenState { ErrorState(items.error, onRetry) }
                    }

                    UiState.Empty -> {
                        FullScreenState {
                            if (state.query.isNotBlank()) {
                                StateMessage(
                                    Icons.Outlined.SearchOff,
                                    "Nada encontrado para \"${state.query}\"",
                                    body = "Confira a grafia ou busque pelo tipo do item.",
                                )
                            } else {
                                StateMessage(Icons.Outlined.Inventory2, "Estoque vazio", body = "Nenhum item em estoque nesta secretaria.")
                            }
                        }
                    }

                    is UiState.Success -> {
                        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                            items(state.visible, key = { it.id }) { item ->
                                StockRow(item, onClick = { detailId = item.id })
                                HorizontalDivider(Modifier.padding(start = Spacing.screen))
                            }
                        }
                    }
                }
            }
        }
    }
    val detail = (state.items as? UiState.Success)?.data?.firstOrNull { it.id == detailId } ?: initialDetail?.takeIf { it.id == detailId }
    if (detail != null) {
        ModalBottomSheet(onDismissRequest = { detailId = null }) {
            Column(
                Modifier.padding(horizontal = Spacing.xxl).padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(detail.model, style = MaterialTheme.typography.titleLarge)
                Text(listOfNotNull(detail.brand, detail.type).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                QuantityBadge(detail)
                val specs =
                    detail.specifications +
                        listOfNotNull(
                            detail.patrimony?.let { "Patrimônio" to it },
                            detail.serialNumber?.let { "Número de série" to it },
                            ("Tipo de controle" to if (detail.consumable) "Consumível" else "Durável"),
                        )
                // Texto alinhado ao título: um ListItem recuaria mais 16 dp e pintaria um fundo
                // de outra cor sobre o sheet.
                specs.forEach { (k, v) ->
                    Column(Modifier.padding(vertical = Spacing.xs)) {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockRow(
    item: StockItem,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.model) },
        supportingContent = { Text(item.summary) },
        trailingContent = { QuantityBadge(item) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun QuantityBadge(item: StockItem) {
    val low = item.isLow
    Surface(
        color = if (low) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (low) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            if (item.quantity == 0) "Sem saldo" else "${item.quantity} un." + if (low) " · baixo" else "",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

// --- Previews ------------------------------------------------------------------------------------

@Composable
private fun StockPreview(
    state: StockUiState,
    detail: StockItem? = null,
) = ChamadosTheme {
    StockScreen(state, {}, {}, {}, null, initialDetail = detail)
}

@PreviewLightDark
@Composable
private fun StockSuccessPreview() = StockPreview(StockUiState(items = UiState.Success(PreviewData.stock)))

@Preview(name = "Carregando")
@Composable
private fun StockLoadingPreview() = StockPreview(StockUiState(items = UiState.Loading))

@Preview(name = "Busca sem resultado")
@Composable
private fun StockEmptySearchPreview() = StockPreview(StockUiState(query = "scanner", items = UiState.Empty))

@Preview(name = "Estoque vazio")
@Composable
private fun StockEmptyPreview() = StockPreview(StockUiState(items = UiState.Empty))

@Preview(name = "Erro")
@Composable
private fun StockErrorPreview() = StockPreview(StockUiState(items = UiState.Error(DomainError.Network())))

@Preview(name = "Detalhe do item")
@Composable
private fun StockDetailPreview() = StockPreview(StockUiState(items = UiState.Success(PreviewData.stock)), PreviewData.stock[0])
