package br.com.portosoftware.chamados.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallHistoryEntry
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.ui.common.ErrorState
import br.com.portosoftware.chamados.ui.common.FullScreenState
import br.com.portosoftware.chamados.ui.common.InfoRow
import br.com.portosoftware.chamados.ui.common.PriorityBadge
import br.com.portosoftware.chamados.ui.common.SectionHeader
import br.com.portosoftware.chamados.ui.common.SkeletonBox
import br.com.portosoftware.chamados.ui.common.StatusChip
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.formatBytes
import br.com.portosoftware.chamados.ui.common.formatDateTime
import br.com.portosoftware.chamados.ui.common.protocolOf
import br.com.portosoftware.chamados.ui.common.relative
import br.com.portosoftware.chamados.ui.preview.PreviewData
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme
import br.com.portosoftware.chamados.ui.theme.Spacing
import kotlinx.coroutines.flow.filter

/** DDD + 9 dígitos; mais que isso já inclui o código do país. */
private const val LOCAL_PHONE_MAX_DIGITS = 11

@Composable
fun CallDetailRoute(
    progressSaved: Boolean,
    onProgressSavedConsume: () -> Unit,
    onBack: () -> Unit,
    onAttend: () -> Unit,
    viewModel: CallDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentProgressSaved by rememberUpdatedState(progressSaved)
    val currentOnProgressSavedConsume by rememberUpdatedState(onProgressSavedConsume)
    LaunchedEffect(viewModel) {
        snapshotFlow { currentProgressSaved }.filter { it }.collect {
            viewModel.onProgressSaved()
            currentOnProgressSavedConsume()
        }
    }
    val context = LocalContext.current
    CallDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onAttend = onAttend,
        onAssume = viewModel::assume,
        onMessageConsume = viewModel::messageShown,
        onContact = { uri ->
            try {
                context.startActivity(
                    Intent(
                        if (uri.startsWith("mailto:")) {
                            Intent.ACTION_SENDTO
                        } else if (uri.startsWith("tel:")) {
                            Intent.ACTION_DIAL
                        } else {
                            Intent.ACTION_VIEW
                        },
                        uri.toUri(),
                    ),
                )
            } catch (_: ActivityNotFoundException) {
                viewModel.showMessage("Nenhum app instalado para abrir este contato.")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(
    state: CallDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAttend: () -> Unit,
    onAssume: () -> Unit,
    onMessageConsume: () -> Unit,
    onContact: (String) -> Unit,
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
    val call = (state.call as? UiState.Success)?.data
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(call?.let { protocolOf(it.id) } ?: "Chamado") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
        bottomBar = {
            if (state.showsAttend) {
                BottomAppBar {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        if (state.canAssume) {
                            FilledTonalButton(onClick = onAssume, enabled = !state.assuming, modifier = Modifier.weight(1f)) {
                                if (state.assuming) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Assumir")
                            }
                        }
                        Button(onClick = onAttend, enabled = !state.assuming, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Build, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Atender")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state.call) {
                UiState.Loading -> DetailSkeleton()
                is UiState.Error -> FullScreenState { ErrorState(s.error, onRetry) }
                UiState.Empty -> Unit
                is UiState.Success -> DetailContent(s.data, onContact)
            }
        }
    }
}

@Composable
private fun DetailContent(
    call: CallDetail,
    onContact: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl), modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(call.status)
                    PriorityBadge(call.priority)
                }
                Text(call.description, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        "Aberto ${call.openedAt.relative()}",
                        call.openedByName?.let { "por $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        call.applicant?.let { applicant -> item { ApplicantCard(applicant, onContact) } }
        item {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
                InfoRow(
                    Icons.Outlined.LocationOn,
                    "Local",
                    listOfNotNull(call.unitName, call.localName).joinToString(" · ").ifBlank { null },
                )
                InfoRow(
                    Icons.Outlined.Category,
                    "Classificação",
                    listOfNotNull(
                        call.categoryName,
                        call.subcategoryName,
                    ).joinToString(" › ").ifBlank {
                        null
                    },
                )
                InfoRow(
                    Icons.Outlined.Computer,
                    "Equipamento",
                    call.equipment?.let { listOfNotNull(it.patrimony, it.model).joinToString(" · ") },
                )
                InfoRow(
                    if (call.technician == null) Icons.Outlined.PersonAdd else Icons.Outlined.Person,
                    "Responsável",
                    call.technician?.name ?: "Sem responsável",
                )
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = Spacing.sm)) }
        item { SectionHeader("Histórico") }
        val history = call.history.sortedByDescending { it.at }
        items(history.size) { index -> TimelineItem(history[index], isLast = index == history.lastIndex) }
        if (call.attachments.isNotEmpty()) {
            item { SectionHeader("Anexos", modifier = Modifier.padding(top = Spacing.md)) }
            items(call.attachments, key = { it.id }) { attachment ->
                ListItem(
                    headlineContent = { Text(attachment.fileName) },
                    supportingContent = { Text(formatBytes(attachment.sizeBytes)) },
                    leadingContent = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                )
            }
        }
    }
}

/** Botões de contato mais compactos: os três cabem numa linha num celular comum. */
private val ContactButtonPadding = PaddingValues(start = Spacing.md, end = Spacing.lg)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ApplicantCard(
    applicant: Applicant,
    onContact: (String) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Solicitante", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(applicant.name, style = MaterialTheme.typography.titleMedium)
            applicant.positionName?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            listOfNotNull(applicant.phone, applicant.email).takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Em tela estreita ou fonte grande o botão que não cabe desce de linha, em vez de
            // quebrar o próprio rótulo ("E-m / ail").
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val digits = applicant.phone?.filter(Char::isDigit).orEmpty()
                if (digits.isNotEmpty()) {
                    OutlinedButton(onClick = { onContact("tel:$digits") }, contentPadding = ContactButtonPadding) {
                        Icon(Icons.Outlined.Phone, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Ligar")
                    }
                    // Telefone já com DDI (55...) não ganha outro 55.
                    val international = if (digits.length > LOCAL_PHONE_MAX_DIGITS && digits.startsWith("55")) digits else "55$digits"
                    OutlinedButton(onClick = { onContact("https://wa.me/$international") }, contentPadding = ContactButtonPadding) {
                        Icon(Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("WhatsApp")
                    }
                }
                applicant.email?.let { email ->
                    OutlinedButton(onClick = { onContact("mailto:$email") }, contentPadding = ContactButtonPadding) {
                        Icon(Icons.Outlined.Email, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("E-mail")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(
    entry: CallHistoryEntry,
    isLast: Boolean,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Spacing.screen)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp).fillMaxHeight()) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.padding(start = Spacing.sm, bottom = Spacing.lg).weight(1f)) {
            Text(
                listOfNotNull(entry.authorName, entry.at.formatDateTime()).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(entry.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBox(width = 96.dp, height = 24.dp)
            SkeletonBox(width = 80.dp, height = 24.dp)
        }
        SkeletonBox(height = 18.dp)
        SkeletonBox(width = 240.dp, height = 18.dp)
        SkeletonBox(height = 120.dp)
        repeat(4) { SkeletonBox(height = 40.dp) }
    }
}

// --- Previews ------------------------------------------------------------------------------------

@Composable
private fun DetailPreview(state: CallDetailUiState) =
    ChamadosTheme {
        CallDetailScreen(state, {}, {}, {}, {}, {}, {})
    }

@PreviewLightDark
@Composable
private fun DetailSuccessPreview() = DetailPreview(CallDetailUiState(UiState.Success(PreviewData.detail), canAttend = true))

@Preview(name = "Aberto sem responsável (Assumir)")
@Composable
private fun DetailAssumePreview() = DetailPreview(CallDetailUiState(UiState.Success(PreviewData.openUnassigned), canAttend = true))

@Preview(name = "Carregando")
@Composable
private fun DetailLoadingPreview() = DetailPreview(CallDetailUiState(UiState.Loading))

@Preview(name = "403")
@Composable
private fun DetailForbiddenPreview() = DetailPreview(CallDetailUiState(UiState.Error(DomainError.Forbidden())))

@Preview(name = "404")
@Composable
private fun DetailNotFoundPreview() = DetailPreview(CallDetailUiState(UiState.Error(DomainError.NotFound())))
