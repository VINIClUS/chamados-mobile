package br.com.portosoftware.chamados.ui.newcall

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.ui.common.Banner
import br.com.portosoftware.chamados.ui.common.ErrorState
import br.com.portosoftware.chamados.ui.common.SelectField
import br.com.portosoftware.chamados.ui.common.SkeletonBox
import br.com.portosoftware.chamados.ui.common.StateMessage
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.categoriesNotLoadedNotice
import br.com.portosoftware.chamados.ui.common.colors
import br.com.portosoftware.chamados.ui.common.dataOrNull
import br.com.portosoftware.chamados.ui.common.formatBytes
import br.com.portosoftware.chamados.ui.common.icon
import br.com.portosoftware.chamados.ui.common.maskPhone
import br.com.portosoftware.chamados.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCallScreen(
    state: NewCallUiState,
    actions: NewCallActions,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (draft.isEmpty) actions.onClose() else confirmDiscard = true }
    BackHandler(enabled = !state.submitting) { if (!actions.onBack()) leave() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Novo chamado")
                        Text(
                            "Etapa ${draft.step.ordinal + 1} de ${WizardStep.entries.size} · ${draft.step.title}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = leave,
                        enabled = !state.submitting,
                    ) { Icon(Icons.Outlined.Close, contentDescription = "Fechar") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    if (draft.step != WizardStep.APPLICANT) {
                        OutlinedButton(
                            onClick = { actions.onBack() },
                            enabled = !state.submitting,
                            modifier = Modifier.weight(1f),
                        ) { Text("Voltar") }
                    }
                    Button(onClick = actions.onNext, enabled = !state.submitting, modifier = Modifier.weight(1f)) {
                        when {
                            state.submitting -> {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }

                            draft.step == WizardStep.REVIEW -> {
                                Text("Abrir chamado")
                            }

                            else -> {
                                Text("Continuar")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            WizardStepper(draft.step)
            state.submitError?.let { Banner("Não foi possível abrir o chamado. ${it.message}") }
            // Rolagem própria por etapa: avançar não deixa a etapa nova no meio da página.
            val scroll = key(draft.step) { rememberScrollState() }
            Column(
                Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = Spacing.screen, vertical = Spacing.md),
            ) {
                when (draft.step) {
                    WizardStep.APPLICANT -> ApplicantStep(state, actions)
                    WizardStep.LOCATION -> LocationStep(state, actions)
                    WizardStep.PROBLEM -> ProblemStep(state, actions)
                    WizardStep.REVIEW -> ReviewStep(state, actions)
                }
            }
        }
    }

    state.quickApplicant?.let { QuickApplicantSheet(it, state, actions) }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Descartar chamado?") },
            text = { Text("As informações preenchidas serão perdidas.") },
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

@Composable
fun WizardStepper(
    current: WizardStep,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WizardStep.entries.forEach { step ->
            val done = step.ordinal < current.ordinal
            val active = step == current
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(
                        if (done || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Concluída",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            "${step.ordinal + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    step.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

// --- Etapa 1 -------------------------------------------------------------------------------------

@Composable
private fun ApplicantStep(
    state: NewCallUiState,
    actions: NewCallActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text("Quem está pedindo o atendimento?", style = MaterialTheme.typography.titleMedium)
        val selected = state.draft.applicant
        if (selected != null) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(selected.name, style = MaterialTheme.typography.titleMedium)
                        selected.contact?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        listOfNotNull(selected.unitName, selected.localName).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = actions.onClearApplicant) { Text("Trocar") }
                }
            }
            return
        }
        OutlinedTextField(
            value = state.applicantQuery,
            onValueChange = actions.onApplicantQuery,
            label = { Text("Buscar por nome") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            isError = state.errors["applicant"] != null,
            // A dica some quando a busca já vale; o texto vazio mantém a altura do campo.
            supportingText = {
                Text(
                    state.errors["applicant"]
                        ?: if (state.applicantQuery.trim().length < NewCallViewModel.MIN_SEARCH) {
                            "Digite pelo menos ${NewCallViewModel.MIN_SEARCH} letras."
                        } else {
                            ""
                        },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        when (val results = state.applicants) {
            null -> {
                StateMessage(
                    Icons.Outlined.PersonSearch,
                    "Busque o solicitante",
                    body = "Se for a primeira vez dele, faça um cadastro rápido.",
                    actionLabel = "Cadastro rápido",
                    onAction = actions.onOpenQuickApplicant,
                )
            }

            UiState.Loading -> {
                Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            UiState.Empty -> {
                StateMessage(
                    Icons.Outlined.PersonAdd,
                    "Ninguém encontrado com \"${state.applicantQuery.trim()}\"",
                    body = "Cadastre agora com nome, contato e local.",
                    actionLabel = "Cadastro rápido",
                    onAction = actions.onOpenQuickApplicant,
                )
            }

            is UiState.Error -> {
                ErrorState(results.error, onRetry = { actions.onApplicantQuery(state.applicantQuery) })
            }

            is UiState.Success -> {
                Column {
                    results.data.forEach { applicant ->
                        ListItem(
                            headlineContent = { Text(applicant.name) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        applicant.positionName,
                                        applicant.unitName,
                                        applicant.localName,
                                    ).joinToString(" · ").ifBlank { "Sem lotação" },
                                )
                            },
                            modifier = Modifier.clickable { actions.onSelectApplicant(applicant) },
                        )
                    }
                    TextButton(onClick = actions.onOpenQuickApplicant) {
                        Icon(Icons.Outlined.PersonAdd, null, Modifier.size(18.dp))
                        Text(" Não encontrou? Cadastro rápido")
                    }
                }
            }
        }
    }
}

// --- Etapa 2 -------------------------------------------------------------------------------------

@Composable
private fun LocationStep(
    state: NewCallUiState,
    actions: NewCallActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        val d = state.draft
        Text("Onde é o atendimento?", style = MaterialTheme.typography.titleMedium)
        if (d.applicant?.localId != null && d.localId == d.applicant.localId) {
            Text(
                "Preenchido com a lotação de ${d.applicant.name}. Altere se for em outro lugar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val units = state.units.dataOrNull.orEmpty()
        SelectField(
            label = "Unidade *",
            options = units,
            selected = units.firstOrNull { it.id == d.unitId },
            optionLabel = { it.name },
            onSelect = { actions.onUnit(it?.id) },
            enabled = state.units is UiState.Success,
            isError = state.errors["unitId"] != null,
            supportingText = state.errors["unitId"] ?: loadingHint(state.units),
        )
        val locals = state.locals?.dataOrNull.orEmpty()
        SelectField(
            label = "Local *",
            options = locals,
            selected = locals.firstOrNull { it.id == d.localId },
            optionLabel = { it.name },
            onSelect = { actions.onLocal(it?.id) },
            enabled = state.locals is UiState.Success,
            isError = state.errors["localId"] != null,
            // A dica de unidade só vale sem unidade; com os locais carregados loadingHint devolve null.
            supportingText =
                state.errors["localId"] ?: state.locals.let { if (it == null) "Escolha a unidade primeiro." else loadingHint(it) },
        )
        val equipment = state.equipment?.dataOrNull.orEmpty()
        SelectField(
            label = "Equipamento (opcional)",
            options = equipment,
            selected = equipment.firstOrNull { it.id == d.equipmentId },
            optionLabel = { listOfNotNull(it.patrimony, it.model).joinToString(" · ") },
            onSelect = { actions.onEquipment(it?.id) },
            enabled = state.equipment is UiState.Success,
            allowNone = "Não se aplica",
            supportingText =
                when (state.equipment) {
                    null -> "Escolha o local para ver os equipamentos."
                    UiState.Empty -> "Nenhum equipamento cadastrado neste local."
                    else -> loadingHint(state.equipment)
                },
        )
    }
}

private fun loadingHint(state: UiState<*>): String? =
    when (state) {
        UiState.Loading -> "Carregando…"
        UiState.Empty -> "Nenhuma opção disponível."
        is UiState.Error -> state.error.message
        is UiState.Success -> null
    }

// --- Etapa 3 -------------------------------------------------------------------------------------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProblemStep(
    state: NewCallUiState,
    actions: NewCallActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        val d = state.draft
        Text("Qual é o problema?", style = MaterialTheme.typography.titleMedium)
        Text("Categoria *", style = MaterialTheme.typography.titleSmall)
        when (val categories = state.categories) {
            UiState.Loading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { repeat(3) { SkeletonBox(width = 88.dp, height = 32.dp) } }
            }

            is UiState.Error -> {
                ErrorState(categories.error, onRetry = null)
            }

            UiState.Empty -> {
                Text("Nenhuma categoria cadastrada.")
            }

            is UiState.Success -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    categories.data.forEach { c ->
                        FilterChip(d.categoryId == c.id, onClick = { actions.onCategory(c.id) }, label = { Text(c.name) })
                    }
                }
            }
        }
        state.errors["categoryId"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.categoriesNotLoaded > 0) {
            Text(
                categoriesNotLoadedNotice(state.categoriesNotLoaded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (val subs = state.subcategories) {
            null -> {}

            UiState.Empty -> {
                Text(
                    "Esta categoria não tem sintomas cadastrados. Escolha outra ou peça o cadastro ao administrador.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            UiState.Loading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { repeat(3) { SkeletonBox(width = 96.dp, height = 32.dp) } }
            }

            is UiState.Error -> {
                Text(subs.error.message, color = MaterialTheme.colorScheme.error)
            }

            is UiState.Success -> {
                Text("Sintoma *", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    subs.data.forEach { s ->
                        val selected = d.subcategoryId == s.id
                        FilterChip(selected, onClick = { actions.onSubcategory(if (selected) null else s.id) }, label = { Text(s.name) })
                    }
                }
                state.errors["subcategoryId"]?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        OutlinedTextField(
            value = d.description,
            onValueChange = actions.onDescription,
            label = { Text("Descrição *") },
            isError = state.errors["description"] != null,
            supportingText = {
                Row {
                    Text(state.errors["description"] ?: "O que acontece, desde quando, quem é afetado.", modifier = Modifier.weight(1f))
                    Text("${d.description.length}/${CallRules.DESCRIPTION_MAX}")
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        Text("Prioridade", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CallPriority.selectable.forEachIndexed { index, priority ->
                val c = priority.colors()
                SegmentedButton(
                    selected = d.priority == priority,
                    onClick = { actions.onPriority(priority) },
                    shape = SegmentedButtonDefaults.itemShape(index, CallPriority.selectable.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = c.container, activeContentColor = c.onContainer),
                    icon = { SegmentedButtonDefaults.Icon(d.priority == priority) { Icon(priority.icon, null, Modifier.size(18.dp)) } },
                ) { Text(priority.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

// --- Etapa 4 -------------------------------------------------------------------------------------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReviewStep(
    state: NewCallUiState,
    actions: NewCallActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        val d = state.draft
        val unit =
            state.units.dataOrNull
                ?.firstOrNull { it.id == d.unitId }
                ?.name
        val local =
            state.locals
                ?.dataOrNull
                ?.firstOrNull { it.id == d.localId }
                ?.name
        val equipment = state.equipment?.dataOrNull?.firstOrNull { it.id == d.equipmentId }
        val category =
            state.categories.dataOrNull
                ?.firstOrNull { it.id == d.categoryId }
                ?.name
        val sub =
            state.subcategories
                ?.dataOrNull
                ?.firstOrNull { it.id == d.subcategoryId }
                ?.name

        Text("Revise e envie", style = MaterialTheme.typography.titleMedium)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(vertical = Spacing.sm)) {
                SummaryRow("Solicitante", d.applicant?.name, state.errors["applicant"]) { actions.onGoToStep(WizardStep.APPLICANT) }
                SummaryRow(
                    "Local",
                    listOfNotNull(
                        unit,
                        local,
                        equipment?.let {
                            listOfNotNull(it.patrimony, it.model).joinToString(" · ")
                        },
                    ).joinToString(" · ").ifBlank { null },
                    state.errors["unitId"] ?: state.errors["localId"],
                ) { actions.onGoToStep(WizardStep.LOCATION) }
                SummaryRow(
                    "Problema",
                    listOfNotNull(
                        listOfNotNull(category, sub).joinToString(" › ").ifBlank {
                            null
                        },
                        d.description.takeIf {
                            it.isNotBlank()
                        },
                        "Prioridade ${d.priority.label.lowercase()}",
                    ).joinToString(
                        "\n",
                    ),
                    state.errors["categoryId"] ?: state.errors["subcategoryId"] ?: state.errors["description"],
                ) { actions.onGoToStep(WizardStep.PROBLEM) }
            }
        }

        Text("Situação inicial", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CallRules.initialStatuses.forEach { status ->
                val selected = d.status == status
                OutlinedCard(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) { actions.onStatus(status) }) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null)
                        Icon(status.icon, null, Modifier.padding(horizontal = Spacing.sm))
                        Column {
                            Text(status.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                when (status) {
                                    CallStatus.OPENED -> "Entra na fila para triagem."
                                    CallStatus.IN_PROGRESS -> "Você já está resolvendo agora."
                                    else -> "Resolvido no contato."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        SelectField(
            label = "Técnico responsável (opcional)",
            options = state.technicians,
            selected = state.technicians.firstOrNull { it.id == d.technicianId },
            optionLabel = { it.name },
            onSelect = { actions.onTechnician(it?.id) },
            allowNone = "Sem responsável",
        )
        if (CallRules.acceptsAttachments(d.status)) {
            Text("Anexos (até 10 MB cada, 25 MB no total)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = actions.onTakePhoto) {
                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp))
                    Text(" Câmera")
                }
                OutlinedButton(onClick = actions.onPickImages) {
                    Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                    Text(" Galeria")
                }
            }
        } else {
            Text(
                "Chamado aberto como Fechado não recebe anexo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.errors["attachments"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            d.attachments.forEach { file ->
                InputChip(
                    selected = false,
                    onClick = { actions.onRemoveAttachment(file.uri) },
                    label = { Text("${file.fileName} · ${formatBytes(file.sizeBytes)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Outlined.Close, "Remover ${file.fileName}", Modifier.size(18.dp)) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String?,
    error: String?,
    onEdit: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = Spacing.sm)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "Não preenchido", style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        TextButton(onClick = onEdit) { Text("Editar") }
    }
}

// --- Cadastro rápido -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickApplicantSheet(
    form: QuickApplicantForm,
    state: NewCallUiState,
    actions: NewCallActions,
) {
    ModalBottomSheet(
        onDismissRequest = actions.onCloseQuickApplicant,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = Spacing.xxl)
                .padding(bottom = Spacing.xxl)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Cadastro rápido", style = MaterialTheme.typography.titleLarge)
            Text(
                "O solicitante fica disponível para os próximos chamados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> actions.onQuickApplicantChange { it.copy(name = v) } },
                label = { Text("Nome *") },
                singleLine = true,
                isError = "name" in form.errors,
                supportingText = form.errors["name"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.email,
                onValueChange = { v -> actions.onQuickApplicantChange { it.copy(email = v) } },
                label = { Text("E-mail") },
                singleLine = true,
                isError = "email" in form.errors,
                supportingText = form.errors["email"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            // O campo guarda a própria seleção: só com a String, a máscara deixava o cursor para trás
            // e cada dígito novo entrava no meio do número.
            var phone by remember { mutableStateOf(TextFieldValue(form.phone, TextRange(form.phone.length))) }
            OutlinedTextField(
                value = phone,
                onValueChange = { v ->
                    phone = maskPhone(v)
                    actions.onQuickApplicantChange { it.copy(phone = phone.text) }
                },
                label = { Text("Telefone") },
                placeholder = { Text("(99)99999-9999") },
                singleLine = true,
                isError = "phone" in form.errors,
                supportingText = form.errors["phone"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            val positions = state.positions.dataOrNull.orEmpty()
            SelectField(
                "Cargo *",
                positions,
                positions.firstOrNull { it.id == form.positionId },
                { it.name },
                { position -> actions.onQuickApplicantChange { it.copy(positionId = position?.id) } },
                enabled = positions.isNotEmpty(),
                isError = "positionId" in form.errors,
                supportingText = form.errors["positionId"] ?: if (positions.isEmpty()) "Nenhum cargo disponível." else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = form.newPositionName,
                    onValueChange = { value -> actions.onQuickApplicantChange { it.withNewPositionName(value) } },
                    label = { Text("Cargo não cadastrado") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = actions.onCreateQuickPosition,
                    enabled = form.newPositionName.isNotBlank() && !form.savingPosition,
                ) {
                    if (form.savingPosition) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Criar")
                    }
                }
            }
            val units = state.units.dataOrNull.orEmpty()
            SelectField(
                "Unidade *",
                units,
                units.firstOrNull { it.id == form.unitId },
                { it.name },
                { actions.onQuickApplicantUnit(it?.id) },
            )
            SelectField(
                "Local *",
                form.locals,
                form.locals.firstOrNull { it.id == form.localId },
                { it.name },
                { l -> actions.onQuickApplicantChange { it.copy(localId = l?.id) } },
                enabled = form.locals.isNotEmpty(),
                isError = "localId" in form.errors,
                supportingText = form.errors["localId"] ?: if (form.unitId == null) "Escolha a unidade primeiro." else null,
            )
            Button(
                onClick = actions.onSaveQuickApplicant,
                enabled = !form.saving,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            ) {
                if (form.saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Cadastrar e usar")
                }
            }
        }
    }
}
