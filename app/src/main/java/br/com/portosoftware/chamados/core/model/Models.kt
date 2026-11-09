package br.com.portosoftware.chamados.core.model

import java.time.LocalDateTime

// Modelos de domínio. Não conhecem JSON nem Retrofit: os DTOs do flavor "remote"
// são convertidos para estes tipos, e os fakes os constroem direto.

/**
 * Igual a `enums/Role.java` do backend, mais [UNKNOWN]: valor que esta versão do app
 * não conhece, ou ausente. Em todos os enums, UNKNOWN não libera ação e nunca é enviado à API.
 */
enum class Role { ADMIN, TECHNICAL_ADMIN, TECHNICAL, VIEWER, SYSTEM_EXTERNAL, UNKNOWN }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class CallStatus(
    val label: String,
) {
    OPENED("Aberto"),
    IN_PROGRESS("Em atendimento"),
    CLOSED("Fechado"),
    UNKNOWN("Desconhecido"),
}

enum class CallPriority(
    val label: String,
) {
    LOW("Baixa"),
    NORMAL("Normal"),
    HIGH("Alta"),
    URGENT("Urgente"),
    UNKNOWN("Desconhecida"),
    ;

    companion object {
        /** O que o usuário escolhe ao criar e ao filtrar. */
        val selectable = listOf(LOW, NORMAL, HIGH, URGENT)
    }
}

data class Secretary(
    val id: String,
    val name: String,
)

data class OrgUnit(
    val id: String,
    val name: String,
)

data class Local(
    val id: String,
    val name: String,
    val unitId: String,
)

data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
    val secretaries: List<Secretary>,
)

data class Technician(
    val id: String,
    val name: String,
)

data class Applicant(
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val unitId: String?,
    val unitName: String?,
    val localId: String?,
    val localName: String?,
    val positionId: String? = null,
    val positionName: String? = null,
)

data class Position(
    val id: String,
    val name: String,
)

data class Category(
    val id: String,
    val name: String,
)

data class SubCategory(
    val id: String,
    val name: String,
    val categoryId: String,
)

data class Equipment(
    val id: String,
    val patrimony: String?,
    val model: String?,
)

data class CallSummary(
    val id: String,
    val description: String,
    val status: CallStatus,
    val priority: CallPriority,
    val openedAt: LocalDateTime,
    val applicantName: String?,
    val unitName: String?,
    val localName: String?,
    val categoryName: String?,
    val subcategoryName: String?,
    val technicianName: String?,
    val equipmentPatrimony: String?,
)

data class CallHistoryEntry(
    val id: String,
    val at: LocalDateTime,
    val description: String,
    val authorName: String?,
)

data class CallAttachment(
    val id: String,
    val fileName: String,
    val contentType: String?,
    val sizeBytes: Long,
)

data class CallDetail(
    val id: String,
    val description: String,
    val status: CallStatus,
    val priority: CallPriority,
    val openedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val applicant: Applicant?,
    val unitName: String?,
    val localName: String?,
    val categoryName: String?,
    val subcategoryName: String?,
    val equipment: Equipment?,
    val technician: Technician?,
    val openedByName: String?,
    val history: List<CallHistoryEntry>,
    val attachments: List<CallAttachment>,
) {
    val isUnassigned: Boolean get() = technician == null
}

data class CallDashboard(
    val totalActive: Long,
    val newCalls: Long,
    val inProgress: Long,
    val urgent: Long,
    val unassigned: Long,
    val recentCalls: List<CallSummary>,
)

/** Filtros da lista de chamados, espelhando `GET /calls/filter`. */
data class CallFilter(
    val search: String = "",
    // Vários status valem como "qualquer um deles"; vazio não filtra.
    val statuses: Set<CallStatus> = emptySet(),
    val priority: CallPriority? = null,
    val unassigned: Boolean = false,
    val categoryId: String? = null,
    val technicianId: String? = null,
    val unitId: String? = null,
) {
    val activeCount: Int
        get() =
            listOf(
                statuses.isNotEmpty(),
                priority != null,
                unassigned,
                categoryId != null,
                technicianId != null,
                unitId != null,
            ).count { it }
}

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    val isLast: Boolean get() = page + 1 >= totalPages

    /** Itens que existem no servidor e não vieram nesta página. */
    val notLoaded: Long get() = (totalElements - items.size).coerceAtLeast(0)
}

data class StockItem(
    val id: String,
    val model: String,
    val brand: String?,
    val type: String?,
    val quantity: Int,
    val consumable: Boolean,
    val patrimony: String?,
    val serialNumber: String?,
    val specifications: List<Pair<String, String>>,
    /** Tipo de peça de que o equipamento só tem uma: instalar substitui a atual, que vai para manutenção. */
    val isUnique: Boolean = false,
) {
    val isLow: Boolean get() = quantity <= LOW_STOCK_THRESHOLD

    /** Quanto dá para usar num atendimento: o backend recusa mais de uma peça de tipo único. */
    val maxPerCall: Int get() = if (isUnique) quantity.coerceAtMost(1) else quantity

    /**
     * Marca, tipo e valores das especificações: "Genérico(a) · Memória RAM · 8GB · DDR4".
     * As especificações distinguem itens do mesmo modelo, como as memórias genéricas.
     */
    val summary: String
        get() = (listOfNotNull(brand, type) + specifications.map { it.second }).filter { it.isNotBlank() }.joinToString(" · ")

    companion object {
        const val LOW_STOCK_THRESHOLD = 2
    }
}

data class UsedStockItem(
    val itemId: String,
    val quantity: Int,
)

data class ProgressRequest(
    val note: String,
    val status: CallStatus,
    val technicianId: String?,
    val stockItems: List<UsedStockItem>,
)

data class NewApplicant(
    val name: String,
    val email: String?,
    val phone: String?,
    val localId: String,
    val positionId: String,
)

data class NewCall(
    val description: String,
    val status: CallStatus,
    val priority: CallPriority,
    val applicantId: String,
    val unitId: String,
    val localId: String,
    val equipmentId: String?,
    val categoryId: String,
    val subcategoryId: String,
    val technicianId: String?,
)

/** Arquivo escolhido pelo usuário; os bytes são lidos só na hora do envio. */
data class PendingAttachment(
    val uri: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
)
