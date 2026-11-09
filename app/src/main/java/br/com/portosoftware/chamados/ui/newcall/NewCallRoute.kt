package br.com.portosoftware.chamados.ui.newcall

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Local
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.SubCategory
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.protocolOf
import br.com.portosoftware.chamados.ui.preview.PreviewData
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme
import br.com.portosoftware.chamados.ui.theme.Spacing
import java.io.File

@Composable
fun NewCallRoute(
    onClose: () -> Unit,
    onOpenCall: (String) -> Unit,
    viewModel: NewCallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val pickImages =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
            viewModel.addAttachments(uris.map { describe(context, it) })
        }
    val takePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = pendingPhoto
            if (saved && uri != null) viewModel.addAttachments(listOf(describe(context, uri.toUri())))
            pendingPhoto = null
        }

    when (val result = state.result) {
        null -> {
            NewCallScreen(
                state = state,
                actions =
                    NewCallActions(
                        onClose = onClose,
                        onNext = viewModel::next,
                        onBack = viewModel::back,
                        onGoToStep = viewModel::goToStep,
                        onApplicantQuery = viewModel::onApplicantQuery,
                        onSelectApplicant = viewModel::selectApplicant,
                        onClearApplicant = viewModel::clearApplicant,
                        onOpenQuickApplicant = viewModel::openQuickApplicant,
                        onCloseQuickApplicant = viewModel::closeQuickApplicant,
                        onQuickApplicantChange = viewModel::updateQuickApplicant,
                        onQuickApplicantUnit = viewModel::quickApplicantUnit,
                        onCreateQuickPosition = viewModel::createQuickPosition,
                        onSaveQuickApplicant = viewModel::saveQuickApplicant,
                        onUnit = viewModel::selectUnit,
                        onLocal = viewModel::selectLocal,
                        onEquipment = viewModel::selectEquipment,
                        onCategory = viewModel::selectCategory,
                        onSubcategory = viewModel::selectSubcategory,
                        onDescription = viewModel::onDescription,
                        onPriority = viewModel::onPriority,
                        onStatus = viewModel::onStatus,
                        onTechnician = viewModel::onTechnician,
                        onTakePhoto = {
                            val uri = newPhotoUri(context)
                            pendingPhoto = uri.toString()
                            takePhoto.launch(uri)
                        },
                        onPickImages = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onRemoveAttachment = viewModel::removeAttachment,
                    ),
            )
        }

        else -> {
            NewCallResultScreen(
                result = result,
                retrying = state.retryingUploads,
                onOpenCall = onOpenCall,
                onRetryUploads = viewModel::retryUploads,
                onStartAnother = viewModel::startAnother,
            )
        }
    }
}

@Composable
fun NewCallResultScreen(
    result: SubmitResult,
    retrying: Boolean,
    onOpenCall: (String) -> Unit,
    onRetryUploads: () -> Unit,
    onStartAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 400.dp).padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                when (result) {
                    is SubmitResult.Created -> {
                        Icon(Icons.Outlined.TaskAlt, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Chamado aberto", style = MaterialTheme.typography.headlineSmall)
                        Text("Protocolo ${protocolOf(result.id)}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Passe o protocolo ao solicitante para acompanhamento.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { onOpenCall(result.id) }, modifier = Modifier.fillMaxWidth()) { Text("Ver chamado") }
                        OutlinedButton(onClick = onStartAnother, modifier = Modifier.fillMaxWidth()) { Text("Abrir outro chamado") }
                    }

                    is SubmitResult.CreatedWithPendingUploads -> {
                        Icon(Icons.Outlined.CloudOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Text("Chamado aberto com pendência", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Text("Protocolo ${protocolOf(result.id)}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "O chamado foi criado, mas os anexos não foram enviados (${result.error.message}).",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onRetryUploads, enabled = !retrying, modifier = Modifier.fillMaxWidth()) {
                            if (retrying) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Tentar enviar anexos de novo")
                            }
                        }
                        OutlinedButton(onClick = { onOpenCall(result.id) }, modifier = Modifier.fillMaxWidth()) { Text("Ver chamado") }
                    }
                }
            }
        }
    }
}

private fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "chamado-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

private fun describe(
    context: Context,
    uri: Uri,
): DraftAttachment {
    var name = uri.lastPathSegment ?: "anexo"
    var size = 0L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    return DraftAttachment(uri.toString(), name, context.contentResolver.getType(uri), size)
}

// --- Previews ------------------------------------------------------------------------------------

private val units = listOf(OrgUnit("un-1", "UBS Centro"), OrgUnit("un-2", "Hospital Municipal"))
private val locals = listOf(Local("lo-1", "Recepção", "un-1"), Local("lo-2", "Farmácia", "un-1"))
private val categories =
    listOf(Category("c1", "Hardware"), Category("c2", "Sistemas"), Category("c3", "Rede e internet"), Category("c4", "Impressão"))
private val draftApplicant = DraftApplicant("ap-1", "Maria Souza", "(31) 3333-1234", "un-1", "UBS Centro", "lo-1", "Recepção")

private val baseState =
    NewCallUiState(
        units = UiState.Success(units),
        locals = UiState.Success(locals),
        equipment = UiState.Empty,
        categories = UiState.Success(categories),
        technicians = listOf(PreviewData.technician),
    )

@Composable
private fun WizardPreview(state: NewCallUiState) = ChamadosTheme { NewCallScreen(state, NewCallActions()) }

@PreviewLightDark
@Composable
private fun Step1Preview() = WizardPreview(baseState.copy(applicantQuery = "mar", applicants = UiState.Success(listOf(PreviewData.maria))))

@Preview(name = "Etapa 1 · busca sem solicitante")
@Composable
private fun Step1EmptyPreview() = WizardPreview(baseState.copy(applicantQuery = "Rogério", applicants = UiState.Empty))

@Preview(name = "Etapa 1 · cadastro rápido")
@Composable
private fun QuickApplicantPreview() =
    WizardPreview(
        baseState.copy(
            quickApplicant = QuickApplicantForm(name = "Rogério Dias", phone = "(31)98888-7777", unitId = "un-1", locals = locals),
        ),
    )

@Preview(name = "Etapa 2 · listas carregando")
@Composable
private fun Step2LoadingPreview() =
    WizardPreview(
        baseState.copy(
            draft = NewCallDraft(step = WizardStep.LOCATION, applicant = draftApplicant, unitId = "un-1"),
            locals = UiState.Loading,
            equipment = null,
        ),
    )

@Preview(name = "Etapa 3 · validação")
@Composable
private fun Step3ErrorPreview() =
    WizardPreview(
        baseState.copy(
            draft =
                NewCallDraft(
                    step = WizardStep.PROBLEM,
                    applicant = draftApplicant,
                    unitId = "un-1",
                    localId = "lo-1",
                    description = "Não liga",
                ),
            errors =
                mapOf(
                    "categoryId" to "Escolha a categoria.",
                    "description" to "Descreva o problema com pelo menos 10 caracteres.",
                ),
        ),
    )

@Preview(name = "Etapa 3 · problema", heightDp = 900)
@Composable
private fun Step3Preview() =
    WizardPreview(
        baseState.copy(
            draft =
                NewCallDraft(
                    step = WizardStep.PROBLEM,
                    applicant = draftApplicant,
                    unitId = "un-1",
                    localId = "lo-1",
                    categoryId = "c1",
                    subcategoryId = "s1",
                    description = "Computador da recepção não liga após queda de energia.",
                    priority = CallPriority.URGENT,
                ),
            subcategories = UiState.Success(listOf(SubCategory("s1", "Não liga", "c1"), SubCategory("s2", "Lentidão", "c1"))),
        ),
    )

@Preview(name = "Etapa 4 · revisão (Fechado sem anexos)", heightDp = 1100)
@Composable
private fun Step4Preview() =
    WizardPreview(
        baseState.copy(
            draft =
                NewCallDraft(
                    step = WizardStep.REVIEW,
                    applicant = draftApplicant,
                    unitId = "un-1",
                    localId = "lo-1",
                    categoryId = "c1",
                    description = "Computador da recepção não liga após queda de energia.",
                    priority = CallPriority.URGENT,
                    status = CallStatus.CLOSED,
                ),
            errors = mapOf("attachments" to "Os anexos foram retirados: chamado fechado não recebe anexo."),
        ),
    )

@Preview(name = "Sucesso")
@Composable
private fun SuccessPreview() = ChamadosTheme { NewCallResultScreen(SubmitResult.Created("c0a81001-abcd"), false, {}, {}, {}) }

@Preview(name = "Criado com pendência")
@Composable
private fun PendingPreview() =
    ChamadosTheme {
        NewCallResultScreen(SubmitResult.CreatedWithPendingUploads("c0a81001-abcd", DomainError.Network()), false, {}, {}, {})
    }
