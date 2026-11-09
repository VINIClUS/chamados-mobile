package br.com.portosoftware.chamados.data.remote

import br.com.portosoftware.chamados.contract.model.CallProgressRequestDTO
import br.com.portosoftware.chamados.contract.model.CallRequestDTO
import br.com.portosoftware.chamados.contract.model.CallResponseDTO
import br.com.portosoftware.chamados.contract.model.CallSummaryResponseDTO
import br.com.portosoftware.chamados.contract.model.UserResponseDTO
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.Role

internal fun CallResponseDTO.Status?.toDomain(): CallStatus = when (this) {
    CallResponseDTO.Status.OPENED -> CallStatus.OPENED
    CallResponseDTO.Status.IN_PROGRESS -> CallStatus.IN_PROGRESS
    CallResponseDTO.Status.CLOSED -> CallStatus.CLOSED
    CallResponseDTO.Status.unknown_default_open_api, null -> CallStatus.UNKNOWN
}

internal fun CallSummaryResponseDTO.Status?.toDomain(): CallStatus = when (this) {
    CallSummaryResponseDTO.Status.OPENED -> CallStatus.OPENED
    CallSummaryResponseDTO.Status.IN_PROGRESS -> CallStatus.IN_PROGRESS
    CallSummaryResponseDTO.Status.CLOSED -> CallStatus.CLOSED
    CallSummaryResponseDTO.Status.unknown_default_open_api, null -> CallStatus.UNKNOWN
}

internal fun CallResponseDTO.Priority?.toDomain(): CallPriority = when (this) {
    CallResponseDTO.Priority.LOW -> CallPriority.LOW
    CallResponseDTO.Priority.NORMAL -> CallPriority.NORMAL
    CallResponseDTO.Priority.HIGH -> CallPriority.HIGH
    CallResponseDTO.Priority.URGENT -> CallPriority.URGENT
    CallResponseDTO.Priority.unknown_default_open_api, null -> CallPriority.UNKNOWN
}

internal fun CallSummaryResponseDTO.Priority?.toDomain(): CallPriority = when (this) {
    CallSummaryResponseDTO.Priority.LOW -> CallPriority.LOW
    CallSummaryResponseDTO.Priority.NORMAL -> CallPriority.NORMAL
    CallSummaryResponseDTO.Priority.HIGH -> CallPriority.HIGH
    CallSummaryResponseDTO.Priority.URGENT -> CallPriority.URGENT
    CallSummaryResponseDTO.Priority.unknown_default_open_api, null -> CallPriority.UNKNOWN
}

internal fun UserResponseDTO.Role?.toDomain(): Role = when (this) {
    UserResponseDTO.Role.ADMIN -> Role.ADMIN
    UserResponseDTO.Role.TECHNICAL_ADMIN -> Role.TECHNICAL_ADMIN
    UserResponseDTO.Role.TECHNICAL -> Role.TECHNICAL
    UserResponseDTO.Role.VIEWER -> Role.VIEWER
    UserResponseDTO.Role.SYSTEM_EXTERNAL -> Role.SYSTEM_EXTERNAL
    UserResponseDTO.Role.unknown_default_open_api, null -> Role.UNKNOWN
}

internal fun CallStatus.toApi(): CallRequestDTO.Status = when (this) {
    CallStatus.OPENED -> CallRequestDTO.Status.OPENED
    CallStatus.IN_PROGRESS -> CallRequestDTO.Status.IN_PROGRESS
    CallStatus.CLOSED -> CallRequestDTO.Status.CLOSED
    CallStatus.UNKNOWN -> throw IllegalArgumentException("Status desconhecido não é enviado à API.")
}

internal fun CallStatus.toProgressApi(): CallProgressRequestDTO.Status = when (this) {
    CallStatus.OPENED -> CallProgressRequestDTO.Status.OPENED
    CallStatus.IN_PROGRESS -> CallProgressRequestDTO.Status.IN_PROGRESS
    CallStatus.CLOSED -> CallProgressRequestDTO.Status.CLOSED
    CallStatus.UNKNOWN -> throw IllegalArgumentException("Status desconhecido não é enviado à API.")
}

internal fun CallPriority.toApi(): CallRequestDTO.Priority = when (this) {
    CallPriority.LOW -> CallRequestDTO.Priority.LOW
    CallPriority.NORMAL -> CallRequestDTO.Priority.NORMAL
    CallPriority.HIGH -> CallRequestDTO.Priority.HIGH
    CallPriority.URGENT -> CallRequestDTO.Priority.URGENT
    CallPriority.UNKNOWN -> throw IllegalArgumentException("Prioridade desconhecida não é enviada à API.")
}
