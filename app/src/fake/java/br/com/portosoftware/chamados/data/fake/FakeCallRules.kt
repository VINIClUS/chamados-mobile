package br.com.portosoftware.chamados.data.fake

import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Equipment
import br.com.portosoftware.chamados.core.model.Local
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.SubCategory
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.core.model.UsedStockItem
import br.com.portosoftware.chamados.core.model.map

// Regras do CallService replicadas no fake. Cada função valida sem alterar nada;
// o repositório só muda o estado depois que todas passam, como a transação do backend.

/** Status que, ao entrar, registram a data de conclusão (CallService.changeStatus). */
internal val completionStatuses = setOf(CallStatus.CLOSED)

internal fun FakeBackend.activeUnits(): List<OrgUnit> = FakeData.units[activeSecretary?.id].orEmpty()

/** Chamado da secretaria ativa; de outra secretaria é "não encontrado", como `findByIdAndUnitSecretaryId`. */
internal fun FakeBackend.visibleCallIndex(id: String): Int {
    val units = activeUnitNames()
    return calls.indexOfFirst { it.id == id && it.unitName in units }
}

internal fun callNotFound() = DomainError.NotFound("Chamado não encontrado.")

/** CallLifecycle.requireOpen */
internal const val CLOSED_CALL_MESSAGE =
    "Chamado fechado não pode ser alterado ou atendido. Um administrador deve reabri-lo com justificativa."

/** CallService.resolveTechnician: nulo remove o técnico; o técnico precisa ser da secretaria ativa. */
internal fun FakeBackend.resolveTechnician(id: String?): AppResult<Technician?> {
    if (id == null) return AppResult.Success(null)
    val technician =
        FakeData.technicians.firstOrNull { it.id == id }
            ?: return AppResult.Failure(DomainError.NotFound("Técnico não encontrado."))
    val secretaries = FakeData.technicianSecretaries[id].orEmpty()
    return if (activeSecretary?.id in secretaries) {
        AppResult.Success(technician)
    } else {
        AppResult.Failure(DomainError.Forbidden("O técnico não pertence à secretaria ativa."))
    }
}

/** CallService.installStockItems, só a parte de validação: nada é baixado se uma linha falhar. */
internal fun FakeBackend.validateStockLines(
    call: CallDetail,
    lines: List<UsedStockItem>,
): DomainError? {
    if (lines.isEmpty()) return null
    if (call.equipment == null) {
        return DomainError.Validation("Vincule um equipamento ao chamado antes de utilizar peças do estoque.")
    }
    val seen = mutableSetOf<String>()
    return lines.firstNotNullOfOrNull { line ->
        val item = stock.firstOrNull { it.id == line.itemId }
        when {
            !seen.add(line.itemId) -> {
                DomainError.Validation("A mesma peça não pode aparecer mais de uma vez no atendimento.")
            }

            line.quantity <= 0 -> {
                DomainError.Validation("Informe uma quantidade positiva para cada peça.")
            }

            item == null -> {
                DomainError.NotFound("Peça de estoque não encontrada.")
            }

            line.quantity > item.quantity -> {
                DomainError.Validation("Saldo insuficiente para ${item.model}.")
            }

            item.isUnique && line.quantity != 1 -> {
                DomainError.Validation("Componentes do tipo ${item.type} só podem ser instalados um por vez.")
            }

            else -> {
                null
            }
        }
    }
}

/** Tudo o que o CallService.save resolve antes de gravar. */
internal data class ResolvedNewCall(
    val status: CallStatus,
    val applicant: Applicant,
    val unit: OrgUnit,
    val local: Local,
    val subcategory: SubCategory,
    val equipment: Equipment?,
    val technician: Technician?,
)

internal fun FakeBackend.resolveNewCall(call: NewCall): AppResult<ResolvedNewCall> {
    // Status inicial fora de Aberto/Em andamento/Fechado vira Aberto (normalizeInitialStatus).
    val status = call.status.takeIf { it in CallRules.initialStatuses } ?: CallStatus.OPENED
    validateText(call)?.let { return AppResult.Failure(it) }
    return resolveLocation(call).flatMap { (unit, local) ->
        resolveClassification(call).flatMap { subcategory ->
            resolveRelated(call, local).flatMap { (applicant, equipment) ->
                resolveTechnician(call.technicianId).map { technician ->
                    ResolvedNewCall(status, applicant, unit, local, subcategory, equipment, technician)
                }
            }
        }
    }
}

private fun validateText(call: NewCall): DomainError? {
    val description = call.description.trim()
    return when {
        description.length < CallRules.DESCRIPTION_MIN -> {
            DomainError.Validation(
                "Descreva o problema com pelo menos ${CallRules.DESCRIPTION_MIN} caracteres.",
                mapOf("description" to "Muito curta"),
            )
        }

        description.length > CallRules.DESCRIPTION_MAX -> {
            DomainError.Validation(
                "A descrição pode ter até ${CallRules.DESCRIPTION_MAX} caracteres.",
                mapOf("description" to "Muito longa"),
            )
        }

        else -> {
            null
        }
    }
}

/** CallService.resolveLocal: o local precisa ser da secretaria ativa e da unidade informada. */
private fun FakeBackend.resolveLocation(call: NewCall): AppResult<Pair<OrgUnit, Local>> {
    val local =
        FakeData.locals.firstOrNull { it.id == call.localId }
            ?: return AppResult.Failure(DomainError.NotFound("Local não encontrado."))
    val unit =
        activeUnits().firstOrNull { it.id == local.unitId }
            ?: return AppResult.Failure(DomainError.Forbidden("O local não pertence à secretaria ativa."))
    return if (call.unitId == unit.id) {
        AppResult.Success(unit to local)
    } else {
        AppResult.Failure(DomainError.Validation("O local não pertence à unidade informada.", mapOf("localId" to "Local de outra unidade")))
    }
}

/** CallService.resolveRequiredSubcategory: categoria e sintoma obrigatórios e coerentes. */
private fun resolveClassification(call: NewCall): AppResult<SubCategory> {
    val subcategory = FakeData.subcategories.firstOrNull { it.id == call.subcategoryId }
    return when {
        call.subcategoryId.isBlank() || call.categoryId.isBlank() -> {
            AppResult.Failure(DomainError.Validation("Informe a categoria e o sintoma/serviço.", mapOf("subcategoryId" to "Obrigatório")))
        }

        subcategory == null -> {
            AppResult.Failure(DomainError.NotFound("Sintoma/serviço não encontrado."))
        }

        subcategory.categoryId != call.categoryId -> {
            AppResult.Failure(DomainError.Validation("O sintoma/serviço não pertence à categoria informada."))
        }

        else -> {
            AppResult.Success(subcategory)
        }
    }
}

/** Solicitante da secretaria ativa (resolveApplicant) e equipamento do local do chamado (resolveEquipment). */
private fun FakeBackend.resolveRelated(
    call: NewCall,
    local: Local,
): AppResult<Pair<Applicant, Equipment?>> {
    val unitIds = activeUnits().map { it.id }
    val applicant =
        applicants.firstOrNull { it.id == call.applicantId && it.unitId in unitIds }
            ?: return AppResult.Failure(DomainError.NotFound("Solicitante não encontrado."))
    val equipmentId = call.equipmentId ?: return AppResult.Success(applicant to null)
    val equipment =
        FakeData.equipment[local.id]?.firstOrNull { it.id == equipmentId }
            ?: return AppResult.Failure(DomainError.Validation("O equipamento não pertence ao local do chamado."))
    return AppResult.Success(applicant to equipment)
}

private inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(value)
        is AppResult.Failure -> this
    }
