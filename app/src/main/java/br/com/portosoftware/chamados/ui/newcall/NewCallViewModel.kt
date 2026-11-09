package br.com.portosoftware.chamados.ui.newcall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.portosoftware.chamados.core.data.ApplicantRepository
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.data.CatalogRepository
import br.com.portosoftware.chamados.core.data.activeSession
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Equipment
import br.com.portosoftware.chamados.core.model.Local
import br.com.portosoftware.chamados.core.model.NewApplicant
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.PendingAttachment
import br.com.portosoftware.chamados.core.model.Position
import br.com.portosoftware.chamados.core.model.SubCategory
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.core.model.map
import br.com.portosoftware.chamados.ui.common.SEARCH_DEBOUNCE_MS
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.dataOrNull
import br.com.portosoftware.chamados.ui.common.toUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Etapas do wizard: uma decisão por tela. */
enum class WizardStep(
    val title: String,
) {
    APPLICANT("Solicitante"),
    LOCATION("Local"),
    PROBLEM("Problema"),
    REVIEW("Revisão"),
}

@Serializable
data class DraftApplicant(
    val id: String,
    val name: String,
    val contact: String? = null,
    val unitId: String? = null,
    val unitName: String? = null,
    val localId: String? = null,
    val localName: String? = null,
)

@Serializable
data class DraftAttachment(
    val uri: String,
    val fileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
)

/**
 * Rascunho do chamado. Fica no SavedStateHandle (como JSON), então sobrevive à rotação
 * e à morte do processo.
 */
@Serializable
data class NewCallDraft(
    val step: WizardStep = WizardStep.APPLICANT,
    val applicant: DraftApplicant? = null,
    val unitId: String? = null,
    val localId: String? = null,
    val equipmentId: String? = null,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val description: String = "",
    val priority: CallPriority = CallPriority.NORMAL,
    val status: CallStatus = CallStatus.OPENED,
    val technicianId: String? = null,
    val attachments: List<DraftAttachment> = emptyList(),
) {
    val isEmpty: Boolean get() = this == NewCallDraft()
}

data class QuickApplicantForm(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val positionId: String? = null,
    val newPositionName: String = "",
    val savingPosition: Boolean = false,
    val unitId: String? = null,
    val localId: String? = null,
    val locals: List<Local> = emptyList(),
    val saving: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    fun withNewPositionName(value: String) = copy(newPositionName = value.take(CallRules.POSITION_NAME_MAX))
}

sealed interface SubmitResult {
    data class Created(
        val id: String,
    ) : SubmitResult

    /** O chamado existe, mas os anexos não subiram. */
    data class CreatedWithPendingUploads(
        val id: String,
        val error: DomainError,
    ) : SubmitResult
}

data class NewCallUiState(
    val draft: NewCallDraft = NewCallDraft(),
    val applicantQuery: String = "",
    val applicants: UiState<List<Applicant>>? = null,
    val units: UiState<List<OrgUnit>> = UiState.Loading,
    val locals: UiState<List<Local>>? = null,
    val equipment: UiState<List<Equipment>>? = null,
    val categories: UiState<List<Category>> = UiState.Loading,
    val categoriesNotLoaded: Long = 0,
    val subcategories: UiState<List<SubCategory>>? = null,
    val technicians: List<Technician> = emptyList(),
    val positions: UiState<List<Position>> = UiState.Loading,
    val errors: Map<String, String> = emptyMap(),
    val quickApplicant: QuickApplicantForm? = null,
    val submitting: Boolean = false,
    val retryingUploads: Boolean = false,
    val submitError: DomainError? = null,
    val result: SubmitResult? = null,
)

/**
 * Dono do rascunho do wizard: cada função pública é uma ação de uma das quatro etapas.
 * Dividir em vários ViewModels espalharia o mesmo rascunho (e o SavedStateHandle) entre eles.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class NewCallViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val calls: CallRepository,
        private val catalog: CatalogRepository,
        private val applicantsRepo: ApplicantRepository,
        auth: AuthRepository,
    ) : ViewModel() {
        private val secretaryId =
            auth
                .activeSession()
                ?.secretary
                ?.id
                .orEmpty()
        private val json = Json { ignoreUnknownKeys = true }

        private val _state =
            MutableStateFlow(
                NewCallUiState(
                    draft =
                        savedStateHandle.get<String>(DRAFT_KEY)?.let { runCatching { json.decodeFromString<NewCallDraft>(it) }.getOrNull() }
                            ?: NewCallDraft(),
                ),
            )
        val state: StateFlow<NewCallUiState> = _state.asStateFlow()
        private var searchJob: Job? = null

        // Uma carga por lista: trocar de unidade/local/categoria cancela a anterior, para uma
        // resposta atrasada não sobrescrever a lista da escolha nova.
        private var localsJob: Job? = null
        private var equipmentJob: Job? = null
        private var subcategoriesJob: Job? = null

        init {
            viewModelScope.launch {
                val units = catalog.units(secretaryId).toUiState { it.isEmpty() }
                _state.update { it.copy(units = units) }
            }
            viewModelScope.launch {
                val page = catalog.categories()
                val categories = page.map { it.items }.toUiState { it.isEmpty() }
                val notLoaded = (page as? AppResult.Success)?.value?.notLoaded ?: 0
                _state.update { it.copy(categories = categories, categoriesNotLoaded = notLoaded) }
            }
            viewModelScope.launch {
                (catalog.technicians() as? AppResult.Success)?.let { r -> _state.update { it.copy(technicians = r.value) } }
            }
            viewModelScope.launch {
                val positions = catalog.positions().toUiState { it.isEmpty() }
                _state.update { it.copy(positions = positions) }
            }
            // Recarrega as listas dependentes de um rascunho restaurado.
            _state.value.draft.unitId
                ?.let { loadLocals(it) }
            _state.value.draft.localId
                ?.let { loadEquipment(it) }
            _state.value.draft.categoryId
                ?.let { loadSubcategories(it) }
        }

        private fun updateDraft(
            clearErrors: Boolean = true,
            transform: (NewCallDraft) -> NewCallDraft,
        ) {
            _state.update { s ->
                val draft = transform(s.draft)
                savedStateHandle[DRAFT_KEY] = json.encodeToString(draft)
                s.copy(draft = draft, errors = if (clearErrors) emptyMap() else s.errors)
            }
        }

        // --- Etapa 1: solicitante ------------------------------------------------------------------

        fun onApplicantQuery(query: String) {
            _state.update { it.copy(applicantQuery = query) }
            searchJob?.cancel()
            if (query.trim().length < MIN_SEARCH) {
                _state.update { it.copy(applicants = null) }
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    _state.update { it.copy(applicants = UiState.Loading) }
                    val result = applicantsRepo.search(query).toUiState { it.isEmpty() }
                    _state.update { it.copy(applicants = result) }
                }
        }

        fun selectApplicant(applicant: Applicant) {
            val previousUnit = _state.value.draft.unitId
            // Solicitante de outra unidade: o local antigo não serve mais, mesmo que ele não tenha local.
            val unitChanged = applicant.unitId != null && applicant.unitId != previousUnit
            updateDraft {
                it.copy(
                    applicant =
                        DraftApplicant(
                            applicant.id,
                            applicant.name,
                            listOfNotNull(applicant.phone, applicant.email).firstOrNull(),
                            applicant.unitId,
                            applicant.unitName,
                            applicant.localId,
                            applicant.localName,
                        ),
                    // A lotação do solicitante preenche o local (editável na etapa 2).
                    unitId = applicant.unitId ?: it.unitId,
                    localId = if (unitChanged) applicant.localId else applicant.localId ?: it.localId,
                    equipmentId = null,
                )
            }
            if (unitChanged) loadLocals(applicant.unitId)
            val localId = _state.value.draft.localId
            if (localId != null) loadEquipment(localId) else _state.update { it.copy(equipment = null) }
        }

        fun clearApplicant() = updateDraft { it.copy(applicant = null) }

        fun openQuickApplicant() =
            _state.update {
                it.copy(
                    quickApplicant =
                        QuickApplicantForm(
                            name =
                                it.applicantQuery
                                    .trim()
                                    .takeIf { q -> q.any(Char::isLetter) }
                                    .orEmpty(),
                        ),
                )
            }

        fun closeQuickApplicant() = _state.update { it.copy(quickApplicant = null) }

        fun updateQuickApplicant(transform: (QuickApplicantForm) -> QuickApplicantForm) =
            _state.update { s -> s.copy(quickApplicant = s.quickApplicant?.let { transform(it).copy(errors = emptyMap(), error = null) }) }

        fun quickApplicantUnit(unitId: String?) {
            updateQuickApplicant { it.copy(unitId = unitId, localId = null, locals = emptyList()) }
            if (unitId != null) {
                viewModelScope.launch {
                    (catalog.locals(unitId) as? AppResult.Success)?.let { r ->
                        _state.update { s -> s.copy(quickApplicant = s.quickApplicant?.copy(locals = r.value)) }
                    }
                }
            }
        }

        fun saveQuickApplicant() {
            val form = _state.value.quickApplicant ?: return
            if (form.saving) return
            val errors = quickApplicantErrors(form)
            val localId = form.localId
            val positionId = form.positionId
            if (errors.isNotEmpty() || localId == null || positionId == null) {
                _state.update { it.copy(quickApplicant = form.copy(errors = errors)) }
                return
            }
            _state.update { it.copy(quickApplicant = form.copy(saving = true)) }
            viewModelScope.launch {
                when (val result = applicantsRepo.create(NewApplicant(form.name, form.email, form.phone, localId, positionId))) {
                    is AppResult.Success -> {
                        _state.update { it.copy(quickApplicant = null) }
                        selectApplicant(result.value)
                    }

                    is AppResult.Failure -> {
                        _state.update {
                            it.copy(
                                quickApplicant =
                                    form.copy(
                                        saving = false,
                                        error = result.error.message,
                                        errors = (result.error as? DomainError.Validation)?.fieldErrors.orEmpty(),
                                    ),
                            )
                        }
                    }
                }
            }
        }

        private fun quickApplicantErrors(form: QuickApplicantForm): Map<String, String> =
            buildMap {
                if (form.name.isBlank()) put("name", "Informe o nome.")
                if (form.localId == null) put("localId", "Escolha o local.")
                if (form.positionId == null) put("positionId", "Escolha o cargo.")
                if (form.email.isNotBlank() && !EMAIL.matches(form.email.trim())) put("email", "E-mail inválido.")
                if (form.phone.isNotBlank() && !PHONE.matches(form.phone)) put("phone", "Use (99)9999-9999 ou (99)99999-9999.")
            }

        /** Cria um cargo sem abandonar o cadastro rápido e já o deixa selecionado. */
        fun createQuickPosition() {
            val form = _state.value.quickApplicant ?: return
            if (form.savingPosition || form.newPositionName.isBlank()) return
            _state.update { it.copy(quickApplicant = form.copy(savingPosition = true, error = null)) }
            viewModelScope.launch {
                when (val result = catalog.createPosition(form.newPositionName)) {
                    is AppResult.Success -> {
                        val current =
                            _state.value.positions.dataOrNull
                                .orEmpty()
                        val positions = (current.filterNot { it.id == result.value.id } + result.value).sortedBy { it.name }
                        _state.update {
                            it.copy(
                                positions = UiState.Success(positions),
                                quickApplicant =
                                    it.quickApplicant?.copy(
                                        positionId = result.value.id,
                                        newPositionName = "",
                                        savingPosition = false,
                                    ),
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        _state.update {
                            it.copy(
                                quickApplicant =
                                    it.quickApplicant?.copy(
                                        savingPosition = false,
                                        error = result.error.message,
                                    ),
                            )
                        }
                    }
                }
            }
        }

        // --- Etapa 2: local ------------------------------------------------------------------------

        fun selectUnit(unitId: String?) {
            updateDraft { it.copy(unitId = unitId, localId = null, equipmentId = null) }
            _state.update { it.copy(equipment = null) }
            equipmentJob?.cancel()
            if (unitId != null) {
                loadLocals(unitId)
            } else {
                localsJob?.cancel()
                _state.update { it.copy(locals = null) }
            }
        }

        fun selectLocal(localId: String?) {
            updateDraft { it.copy(localId = localId, equipmentId = null) }
            if (localId != null) {
                loadEquipment(localId)
            } else {
                equipmentJob?.cancel()
                _state.update { it.copy(equipment = null) }
            }
        }

        fun selectEquipment(equipmentId: String?) = updateDraft { it.copy(equipmentId = equipmentId) }

        private fun loadLocals(unitId: String) {
            _state.update { it.copy(locals = UiState.Loading) }
            localsJob?.cancel()
            localsJob =
                viewModelScope.launch {
                    val result = catalog.locals(unitId).toUiState { it.isEmpty() }
                    _state.update { it.copy(locals = result) }
                }
        }

        private fun loadEquipment(localId: String) {
            _state.update { it.copy(equipment = UiState.Loading) }
            equipmentJob?.cancel()
            equipmentJob =
                viewModelScope.launch {
                    val result = catalog.equipment(localId).toUiState { it.isEmpty() }
                    _state.update { it.copy(equipment = result) }
                }
        }

        // --- Etapa 3: problema ---------------------------------------------------------------------

        fun selectCategory(categoryId: String) {
            if (_state.value.draft.categoryId == categoryId) return
            updateDraft { it.copy(categoryId = categoryId, subcategoryId = null) }
            loadSubcategories(categoryId)
        }

        private fun loadSubcategories(categoryId: String) {
            _state.update { it.copy(subcategories = UiState.Loading) }
            subcategoriesJob?.cancel()
            subcategoriesJob =
                viewModelScope.launch {
                    val result = catalog.subcategories(categoryId).toUiState { it.isEmpty() }
                    _state.update { it.copy(subcategories = result) }
                }
        }

        fun selectSubcategory(id: String?) = updateDraft { it.copy(subcategoryId = id) }

        fun onDescription(value: String) = updateDraft { it.copy(description = value.take(CallRules.DESCRIPTION_MAX)) }

        fun onPriority(priority: CallPriority) = updateDraft { it.copy(priority = priority) }

        // --- Etapa 4: revisão ----------------------------------------------------------------------

        /** Chamado aberto como fechado não recebe anexo: os escolhidos saem do rascunho, com aviso, em vez de não subir em silêncio. */
        fun onStatus(status: CallStatus) {
            val dropsAttachments =
                !CallRules.acceptsAttachments(status) &&
                    _state.value.draft.attachments
                        .isNotEmpty()
            updateDraft { it.copy(status = status, attachments = if (dropsAttachments) emptyList() else it.attachments) }
            if (dropsAttachments) {
                _state.update { it.copy(errors = mapOf("attachments" to "Os anexos foram retirados: chamado fechado não recebe anexo.")) }
            }
        }

        fun onTechnician(id: String?) = updateDraft { it.copy(technicianId = id) }

        /** Recusa aqui o que o backend recusaria depois de o chamado já existir, quando reenviar não adianta. */
        fun addAttachments(files: List<DraftAttachment>) {
            val draft = _state.value.draft
            if (!CallRules.acceptsAttachments(draft.status)) return
            val (allowed, wrongFormat) = files.partition { CallRules.isAttachmentFormatAllowed(it.fileName) }
            val (sized, tooBig) = allowed.partition { it.sizeBytes <= CallRules.MAX_ATTACHMENT_BYTES }
            val accepted = draft.attachments.toMutableList()
            val overTotal = mutableListOf<DraftAttachment>()
            sized.distinctBy { it.uri }.filter { file -> accepted.none { it.uri == file.uri } }.forEach { file ->
                if (CallRules.fitsUploadRequest((accepted + file).map { it.sizeBytes })) accepted += file else overTotal += file
            }
            updateDraft { it.copy(attachments = accepted) }
            val problems =
                listOf(
                    wrongFormat to "formato não aceito. Use PDF, imagem PNG ou JPG, texto, Word ou Excel.",
                    tooBig to "passa de 10 MB.",
                    overTotal to "não cabe: o envio todo pode ter até 25 MB.",
                ).filter { (rejected, _) -> rejected.isNotEmpty() }
                    .joinToString(" ") { (rejected, reason) -> "${rejected.joinToString { it.fileName }}: $reason" }
            if (problems.isNotEmpty()) _state.update { it.copy(errors = mapOf("attachments" to problems)) }
        }

        fun removeAttachment(uri: String) = updateDraft { d -> d.copy(attachments = d.attachments.filterNot { it.uri == uri }) }

        fun goToStep(step: WizardStep) = updateDraft { it.copy(step = step) }

        // --- Navegação entre etapas ----------------------------------------------------------------

        /** Valida a etapa atual; se estiver ok, avança. Na última, envia. */
        fun next() {
            val s = _state.value
            val errors = validate(s.draft.step, s.draft)
            if (errors.isNotEmpty()) {
                _state.update { it.copy(errors = errors) }
                return
            }
            val nextStep = WizardStep.entries.getOrNull(s.draft.step.ordinal + 1)
            if (nextStep != null) updateDraft { it.copy(step = nextStep) } else submit()
        }

        /** Volta uma etapa. Retorna false na primeira etapa (a tela decide se fecha). */
        fun back(): Boolean {
            val prev = WizardStep.entries.getOrNull(_state.value.draft.step.ordinal - 1) ?: return false
            updateDraft { it.copy(step = prev) }
            return true
        }

        private fun submit() {
            if (_state.value.submitting) return
            val d = _state.value.draft
            // As etapas já validaram esses campos; um rascunho incompleto não chega aqui.
            val newCall = d.toNewCall() ?: return
            _state.update { it.copy(submitting = true, submitError = null) }
            viewModelScope.launch {
                when (val created = calls.create(newCall)) {
                    is AppResult.Failure -> {
                        _state.update {
                            it.copy(
                                submitting = false,
                                submitError = created.error,
                                errors = (created.error as? DomainError.Validation)?.fieldErrors.orEmpty(),
                            )
                        }
                    }

                    is AppResult.Success -> {
                        val id = created.value.id
                        val result =
                            if (d.attachments.isEmpty()) {
                                SubmitResult.Created(id)
                            } else {
                                // Anexos sobem numa segunda chamada; se falhar, o chamado já existe.
                                when (val upload = calls.uploadAttachments(id, d.attachments.map { it.toPending() })) {
                                    is AppResult.Success -> SubmitResult.Created(id)
                                    is AppResult.Failure -> SubmitResult.CreatedWithPendingUploads(id, upload.error)
                                }
                            }
                        savedStateHandle.remove<String>(DRAFT_KEY)
                        _state.update { it.copy(submitting = false, result = result) }
                    }
                }
            }
        }

        fun retryUploads() {
            val pending = _state.value.result as? SubmitResult.CreatedWithPendingUploads ?: return
            if (_state.value.retryingUploads) return
            _state.update { it.copy(retryingUploads = true) }
            viewModelScope.launch {
                val upload =
                    calls.uploadAttachments(
                        pending.id,
                        _state.value.draft.attachments
                            .map { it.toPending() },
                    )
                _state.update {
                    it.copy(
                        retryingUploads = false,
                        result =
                            when (upload) {
                                is AppResult.Success -> SubmitResult.Created(pending.id)
                                is AppResult.Failure -> pending.copy(error = upload.error)
                            },
                    )
                }
            }
        }

        fun startAnother() {
            savedStateHandle.remove<String>(DRAFT_KEY)
            _state.update {
                NewCallUiState(
                    units = it.units,
                    categories = it.categories,
                    categoriesNotLoaded = it.categoriesNotLoaded,
                    technicians = it.technicians,
                )
            }
        }

        companion object {
            const val DRAFT_KEY = "new_call_draft"
            const val MIN_SEARCH = 2
            private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
            private val PHONE = Regex("^\\(\\d{2}\\)\\d{4,5}-\\d{4}$")

            /** Regras de cada etapa; puras para poderem ser testadas sem Android. */
            fun validate(
                step: WizardStep,
                d: NewCallDraft,
            ): Map<String, String> =
                buildMap {
                    when (step) {
                        WizardStep.APPLICANT -> {
                            if (d.applicant == null) put("applicant", "Escolha quem está pedindo o atendimento.")
                        }

                        WizardStep.LOCATION -> {
                            if (d.unitId == null) put("unitId", "Escolha a unidade.")
                            if (d.localId == null) put("localId", "Escolha o local.")
                        }

                        WizardStep.PROBLEM -> {
                            // O backend exige categoria e sintoma (CallService.resolveRequiredSubcategory).
                            if (d.categoryId == null) {
                                put("categoryId", "Escolha a categoria.")
                            } else if (d.subcategoryId == null) {
                                put("subcategoryId", "Escolha o sintoma.")
                            }
                            if (d.description.trim().length < CallRules.DESCRIPTION_MIN) {
                                put("description", "Descreva o problema com pelo menos ${CallRules.DESCRIPTION_MIN} caracteres.")
                            }
                        }

                        WizardStep.REVIEW -> {
                            WizardStep.entries.filter { it != WizardStep.REVIEW }.forEach { putAll(validate(it, d)) }
                        }
                    }
                }
        }
    }

private fun DraftAttachment.toPending() = PendingAttachment(uri, fileName, mimeType, sizeBytes)

private fun NewCallDraft.toNewCall(): NewCall? {
    val applicantId = applicant?.id ?: return null
    val unit = unitId ?: return null
    val local = localId ?: return null
    val category = categoryId ?: return null
    val subcategory = subcategoryId ?: return null
    return NewCall(
        description = description,
        status = status,
        priority = priority,
        applicantId = applicantId,
        unitId = unit,
        localId = local,
        equipmentId = equipmentId,
        categoryId = category,
        subcategoryId = subcategory,
        technicianId = technicianId,
    )
}
