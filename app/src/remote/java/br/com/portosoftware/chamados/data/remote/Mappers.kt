package br.com.portosoftware.chamados.data.remote

import br.com.portosoftware.chamados.contract.model.CallAttachmentResponseDTO
import br.com.portosoftware.chamados.contract.model.CallDashboardResponseDTO
import br.com.portosoftware.chamados.contract.model.CallProgressRequestDTO
import br.com.portosoftware.chamados.contract.model.CallRequestDTO
import br.com.portosoftware.chamados.contract.model.CallResponseDTO
import br.com.portosoftware.chamados.contract.model.CallStockItemRequestDTO
import br.com.portosoftware.chamados.contract.model.CallSummaryResponseDTO
import br.com.portosoftware.chamados.contract.model.PageMetadata
import br.com.portosoftware.chamados.contract.model.UserResponseDTO
import br.com.portosoftware.chamados.core.model.CallAttachment
import br.com.portosoftware.chamados.core.model.CallDashboard
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallHistoryEntry
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.Page
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.core.model.User
import java.time.LocalDateTime

private fun String?.required(field: String): String = this ?: error("Campo obrigatório ausente: $field")

internal fun String.toLocalDateTime(): LocalDateTime = LocalDateTime.parse(this)

internal val UserResponseDTO.displayName: String get() = name ?: "Sem nome"

internal fun UserResponseDTO.toTechnician() = Technician(id.required("id"), displayName)

internal fun UserResponseDTO.toDomain() = User(
    id = id.required("id"),
    name = displayName,
    email = email.orEmpty(),
    role = role.toDomain(),
    secretaries = secretaries.orEmpty().map { Secretary(it.id.required("secretaries.id"), it.name.required("secretaries.name")) },
)

internal fun CallSummaryResponseDTO.toDomain() = CallSummary(
    id = id.required("id"),
    description = description.orEmpty(),
    status = status.toDomain(),
    priority = priority.toDomain(),
    openedAt = openingDate.required("openingDate").toLocalDateTime(),
    applicantName = applicantName,
    unitName = unitName,
    localName = localName,
    categoryName = categoryName,
    subcategoryName = subcategoryName,
    technicianName = responsibleTechnicianName,
    equipmentPatrimony = equipmentPatrimony,
)

internal fun <T> PageMetadata.toDomain(items: List<T>): Page<T> = Page(
    items = items,
    page = Math.toIntExact(number ?: 0),
    totalPages = Math.toIntExact(totalPages ?: 0),
    totalElements = totalElements ?: 0,
)

internal fun CallDashboardResponseDTO.toDomain() = CallDashboard(
    totalActive = totalActive ?: 0,
    newCalls = newCalls ?: 0,
    inProgress = inProgress ?: 0,
    urgent = urgent ?: 0,
    unassigned = unassigned ?: 0,
    recentCalls = recentCalls.orEmpty().map { it.toDomain() },
)

internal fun CallAttachmentResponseDTO.toDomain() = CallAttachment(
    id = id.required("id"),
    fileName = originalFileName.required("originalFileName"),
    contentType = contentType,
    sizeBytes = propertySize ?: 0,
)

internal fun CallResponseDTO.toDomain() = CallDetail(
    id = id.required("id"),
    description = description.orEmpty(),
    status = status.toDomain(),
    priority = priority.toDomain(),
    openedAt = openingDate.required("openingDate").toLocalDateTime(),
    completedAt = completionDate?.toLocalDateTime(),
    applicant = applicant?.toDomain(),
    unitName = unit?.name,
    localName = local?.name,
    categoryName = subcategory?.category?.name,
    subcategoryName = subcategory?.name,
    equipment = equipment?.toDomain(),
    technician = responsibleTechnician?.toTechnician(),
    openedByName = openedBy?.displayName,
    history = history.orEmpty().map {
        CallHistoryEntry(
            id = it.id.required("history.id"),
            at = it.eventDate.required("history.eventDate").toLocalDateTime(),
            description = it.description.orEmpty(),
            authorName = it.responsibleUser?.displayName,
        )
    }.sortedBy { it.at },
    attachments = attachments.orEmpty().map { it.toDomain() },
)

internal fun ProgressRequest.toDto() = CallProgressRequestDTO(
    note = note.trim(),
    status = status.toProgressApi(),
    responsibleTechnicianId = technicianId,
    stockItems = stockItems.map { CallStockItemRequestDTO(it.itemId, it.quantity) }.ifEmpty { null },
)

internal fun NewCall.toDto() = CallRequestDTO(
    description = description.trim(),
    status = status.toApi(),
    priority = priority.toApi(),
    equipmentId = equipmentId,
    subcategoryId = subcategoryId,
    categoryId = categoryId,
    applicantId = applicantId,
    localId = localId,
    unitId = unitId,
    responsibleTechnicianId = technicianId,
)
