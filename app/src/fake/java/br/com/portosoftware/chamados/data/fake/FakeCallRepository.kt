package br.com.portosoftware.chamados.data.fake

import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.CallAttachment
import br.com.portosoftware.chamados.core.model.CallDashboard
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallHistoryEntry
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.Page
import br.com.portosoftware.chamados.core.model.PendingAttachment
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.Technician
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** O Início mostra os seis chamados mais recentes (findTop6...OrderByOpeningDateDesc). */
private const val RECENT_CALLS = 6

/** Separa os ids gerados no app dos ids da massa inicial. */
private const val CREATED_ID_OFFSET = 4096

@Singleton
class FakeCallRepository
    @Inject
    constructor(
        private val backend: FakeBackend,
    ) : CallRepository {
        private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 8)
        override val changes: SharedFlow<String> = _changes.asSharedFlow()

        private fun FakeBackend.visibleCalls(): List<CallDetail> {
            val units = activeUnitNames()
            return calls.filter { it.unitName in units }.sortedByDescending { it.openedAt }
        }

        /** Mesmos contadores do DashboardService.generateCallSummary. */
        override suspend fun dashboard(): AppResult<CallDashboard> =
            backend.call {
                val visible = visibleCalls()
                val active = visible.filter { it.status in CallRules.activeStatuses }
                ok(
                    CallDashboard(
                        totalActive = active.size.toLong(),
                        newCalls = visible.count { it.status == CallStatus.OPENED }.toLong(),
                        inProgress = visible.count { it.status == CallStatus.IN_PROGRESS }.toLong(),
                        urgent = active.count { it.priority == CallPriority.URGENT }.toLong(),
                        unassigned = active.count { it.technician == null }.toLong(),
                        recentCalls = visible.take(RECENT_CALLS).map { it.toSummary() },
                    ),
                )
            }

        override suspend fun search(
            filter: CallFilter,
            page: Int,
            size: Int,
        ): AppResult<Page<CallSummary>> =
            backend.call {
                if (filter.search.trim().equals(FakeData.ERROR_TRIGGER, ignoreCase = true)) {
                    return@call fail(DomainError.Network())
                }
                val matches = visibleCalls().filter { it.matches(filter) }
                val totalPages = if (matches.isEmpty()) 0 else (matches.size + size - 1) / size
                val items = matches.drop(page * size).take(size).map { it.toSummary() }
                ok(Page(items, page, totalPages, matches.size.toLong()))
            }

        override suspend fun detail(id: String): AppResult<CallDetail> =
            backend.call {
                val index = visibleCallIndex(id)
                if (index < 0) fail(callNotFound()) else ok(calls[index])
            }

        /** CallService.progress: valida tudo antes de mudar o chamado ou o estoque. */
        override suspend fun registerProgress(
            id: String,
            request: ProgressRequest,
        ): AppResult<CallDetail> =
            backend.call {
                val index = visibleCallIndex(id)
                if (index < 0) return@call fail(callNotFound())
                val current = calls[index]
                if (request.note.isBlank()) {
                    return@call fail(DomainError.Validation("Descreva o que foi feito.", mapOf("note" to "Obrigatório")))
                }
                val statusChanged = request.status != current.status
                if (statusChanged && request.status !in CallRules.allowedTransitions(current.status)) {
                    return@call fail(
                        DomainError.Validation("Transição de ${current.status.label} para ${request.status.label} não é permitida."),
                    )
                }
                val technician =
                    when (val resolved = resolveTechnician(request.technicianId)) {
                        is AppResult.Failure -> return@call resolved
                        is AppResult.Success -> resolved.value
                    }
                validateStockLines(current, request.stockItems)?.let { return@call fail(it) }

                val updated = applyProgress(current, request, technician)
                calls[index] = updated
                _changes.tryEmit(id)
                ok(updated)
            }

        private fun FakeBackend.applyProgress(
            current: CallDetail,
            request: ProgressRequest,
            technician: Technician?,
        ): CallDetail {
            val now = LocalDateTime.now()
            val parts =
                request.stockItems.joinToString { used ->
                    val i = stock.indexOfFirst { it.id == used.itemId }
                    stock[i] = stock[i].copy(quantity = stock[i].quantity - used.quantity)
                    "${used.quantity}× ${stock[i].model}"
                }
            val statusChanged = request.status != current.status
            val text =
                buildString {
                    append(request.note.trim())
                    if (technician?.id != current.technician?.id) {
                        append(technician?.let { "\nTécnico responsável alterado para: ${it.name}." } ?: "\nTécnico responsável removido.")
                    }
                    if (statusChanged) append("\nStatus: ${current.status.label} → ${request.status.label}.")
                    if (parts.isNotEmpty()) append("\nPeças: $parts.")
                }
            return current.copy(
                status = request.status,
                technician = technician,
                // A data de conclusão só muda junto com o status (CallService.changeStatus).
                completedAt =
                    when {
                        !statusChanged -> current.completedAt
                        request.status in completionStatuses -> now
                        else -> null
                    },
                history = current.history + CallHistoryEntry(nextId("h"), now, text, currentUser?.name),
            )
        }

        override suspend fun create(call: NewCall): AppResult<CallDetail> =
            backend.call {
                when (val resolved = resolveNewCall(call)) {
                    is AppResult.Failure -> {
                        resolved
                    }

                    is AppResult.Success -> {
                        val created = newCallDetail(call, resolved.value)
                        calls.add(0, created)
                        _changes.tryEmit(created.id)
                        ok(created)
                    }
                }
            }

        private fun FakeBackend.newCallDetail(
            call: NewCall,
            resolved: ResolvedNewCall,
        ): CallDetail {
            val now = LocalDateTime.now()
            val n = calls.size + 1
            val history =
                buildList {
                    add(
                        CallHistoryEntry(
                            nextId("h"),
                            now,
                            "Chamado registrado com status ${resolved.status.label} e prioridade ${call.priority.label}.",
                            currentUser?.name,
                        ),
                    )
                    resolved.technician?.let {
                        add(CallHistoryEntry(nextId("h"), now, "Técnico responsável atribuído: ${it.name}.", currentUser?.name))
                    }
                }
            return CallDetail(
                id = "c0a8%04x-7f1e-4b2a-9c3d-%012d".format(Locale.ROOT, n + CREATED_ID_OFFSET, n),
                description = call.description.trim(),
                status = resolved.status,
                priority = call.priority,
                openedAt = now,
                completedAt = if (resolved.status == CallStatus.CLOSED) now else null,
                applicant = resolved.applicant,
                unitName = resolved.unit.name,
                localName = resolved.local.name,
                categoryName = FakeData.categories.firstOrNull { it.id == call.categoryId }?.name,
                subcategoryName = resolved.subcategory.name,
                equipment = resolved.equipment,
                technician = resolved.technician,
                openedByName = currentUser?.name,
                history = history,
                attachments = emptyList(),
            )
        }

        override suspend fun uploadAttachments(
            callId: String,
            files: List<PendingAttachment>,
        ): AppResult<List<CallAttachment>> =
            backend.call {
                val index = visibleCallIndex(callId)
                if (index < 0) return@call fail(callNotFound())
                if (!CallRules.acceptsAttachments(calls[index].status)) {
                    return@call fail(DomainError.Validation(CLOSED_CALL_MESSAGE))
                }
                // Arquivos com "falha" no nome simulam o estado "criado com pendências".
                if (files.any { it.fileName.contains("falha", ignoreCase = true) }) return@call fail(DomainError.Network())
                val tooBig = files.firstOrNull { it.sizeBytes > CallRules.MAX_ATTACHMENT_BYTES }
                if (tooBig != null) return@call fail(DomainError.Validation("${tooBig.fileName} passa de 10 MB."))
                if (files.any { !CallRules.isAttachmentFormatAllowed(it.fileName) }) {
                    return@call fail(DomainError.Validation("Formato não permitido. Use PDF, imagem, texto, Word ou Excel."))
                }
                if (!CallRules.fitsUploadRequest(files.map { it.sizeBytes })) {
                    return@call fail(DomainError.Validation("Os anexos passam do limite de 25 MB por envio."))
                }
                val added = files.map { CallAttachment(nextId("at"), it.fileName, it.mimeType, it.sizeBytes) }
                calls[index] = calls[index].copy(attachments = calls[index].attachments + added)
                _changes.tryEmit(callId)
                ok(added)
            }
    }

internal fun CallDetail.toSummary() =
    CallSummary(
        id = id,
        description = description,
        status = status,
        priority = priority,
        openedAt = openedAt,
        applicantName = applicant?.name,
        unitName = unitName,
        localName = localName,
        categoryName = categoryName,
        subcategoryName = subcategoryName,
        technicianName = technician?.name,
        equipmentPatrimony = equipment?.patrimony,
    )
