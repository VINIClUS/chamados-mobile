package br.com.portosoftware.chamados.data.fake

import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallFilter

/** CallSpecification.withFilters: busca livre em texto, pessoas, lugares, equipamento e classificação. */
internal fun CallDetail.matchesText(query: String): Boolean {
    if (query.isEmpty() || id.equals(query, ignoreCase = true)) return true
    val fields =
        listOf(
            description,
            applicant?.name,
            unitName,
            localName,
            technician?.name,
            equipment?.patrimony,
            categoryName,
            subcategoryName,
        )
    return fields.any { it?.contains(query, ignoreCase = true) == true }
}

internal fun CallDetail.matches(filter: CallFilter): Boolean {
    val unitName =
        filter.unitId?.let { id ->
            FakeData.units.values
                .flatten()
                .firstOrNull { it.id == id }
                ?.name
        }
    val categoryName = filter.categoryId?.let { id -> FakeData.categories.firstOrNull { it.id == id }?.name }
    val checks =
        listOf(
            filter.statuses.isEmpty() || status in filter.statuses,
            filter.priority == null || priority == filter.priority,
            !filter.unassigned || technician == null,
            filter.technicianId == null || technician?.id == filter.technicianId,
            filter.unitId == null || this.unitName == unitName,
            filter.categoryId == null || this.categoryName == categoryName,
        )
    return checks.all { it } && matchesText(filter.search.trim())
}
