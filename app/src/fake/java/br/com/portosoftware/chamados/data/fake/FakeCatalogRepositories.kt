package br.com.portosoftware.chamados.data.fake

import br.com.portosoftware.chamados.core.data.ApplicantRepository
import br.com.portosoftware.chamados.core.data.CatalogRepository
import br.com.portosoftware.chamados.core.data.StockRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Equipment
import br.com.portosoftware.chamados.core.model.Local
import br.com.portosoftware.chamados.core.model.NewApplicant
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.Page
import br.com.portosoftware.chamados.core.model.Position
import br.com.portosoftware.chamados.core.model.RolePolicy
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.SubCategory
import br.com.portosoftware.chamados.core.model.Technician
import javax.inject.Inject
import javax.inject.Singleton

/** Máximo de solicitantes devolvidos por busca. */
private const val SEARCH_LIMIT = 20

@Singleton
class FakeCatalogRepository
    @Inject
    constructor(
        private val backend: FakeBackend,
    ) : CatalogRepository {
        override suspend fun units(secretaryId: String): AppResult<List<OrgUnit>> =
            backend.call { ok(FakeData.units[secretaryId].orEmpty()) }

        override suspend fun locals(unitId: String): AppResult<List<Local>> =
            backend.call { ok(FakeData.locals.filter { it.unitId == unitId }) }

        override suspend fun equipment(localId: String): AppResult<List<Equipment>> =
            backend.call { ok(FakeData.equipment[localId].orEmpty()) }

        override suspend fun categories(): AppResult<Page<Category>> =
            backend.call {
                ok(Page(FakeData.categories, 0, 1, FakeData.categories.size.toLong()))
            }

        override suspend fun subcategories(categoryId: String): AppResult<List<SubCategory>> =
            backend.call { ok(FakeData.subcategories.filter { it.categoryId == categoryId }) }

        /** `GET /users/technicians`: só técnicos da secretaria ativa. */
        override suspend fun technicians(): AppResult<List<Technician>> =
            backend.call {
                if (currentUser?.let { RolePolicy(it.role).canListTechnicians } != true) {
                    return@call fail(DomainError.Forbidden())
                }
                ok(FakeData.technicians.filter { activeSecretary?.id in FakeData.technicianSecretaries[it.id].orEmpty() })
            }

        override suspend fun positions(): AppResult<List<Position>> = backend.call { ok(positions.sortedBy { it.name }) }

        override suspend fun createPosition(name: String): AppResult<Position> =
            backend.call {
                val normalized = name.trim()
                if (normalized.isBlank()) return@call fail(DomainError.Validation("Informe o nome do cargo."))
                if (normalized.length > CallRules.POSITION_NAME_MAX) {
                    return@call fail(DomainError.Validation("O nome do cargo pode ter até ${CallRules.POSITION_NAME_MAX} caracteres."))
                }
                val existing = positions.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                if (existing != null) return@call ok(existing)
                val created = Position(nextId("pos"), normalized)
                positions += created
                ok(created)
            }
    }

@Singleton
class FakeApplicantRepository
    @Inject
    constructor(
        private val backend: FakeBackend,
    ) : ApplicantRepository {
        override suspend fun search(query: String): AppResult<List<Applicant>> =
            backend.call {
                val q = query.trim()
                val unitIds = activeUnits().map { it.id }
                ok(applicants.filter { it.unitId in unitIds && it.name.contains(q, ignoreCase = true) }.take(SEARCH_LIMIT))
            }

        override suspend fun create(applicant: NewApplicant): AppResult<Applicant> =
            backend.call {
                validateApplicant(applicant)?.let { return@call fail(it) }
                // ApplicantService.requireLocalInActiveSecretary
                val local = FakeData.locals.firstOrNull { it.id == applicant.localId }
                val unit =
                    activeUnits().firstOrNull { it.id == local?.unitId }
                        ?: return@call fail(DomainError.Forbidden("O local não pertence à secretaria ativa."))
                val position =
                    positions.firstOrNull { it.id == applicant.positionId }
                        ?: return@call fail(
                            DomainError.Validation("Selecione um cargo válido.", mapOf("positionId" to "Obrigatório")),
                        )
                val created =
                    Applicant(
                        id = nextId("ap"),
                        name = applicant.name.trim(),
                        email = applicant.email?.takeIf { it.isNotBlank() },
                        phone = applicant.phone?.takeIf { it.isNotBlank() },
                        unitId = unit.id,
                        unitName = unit.name,
                        localId = local?.id,
                        localName = local?.name,
                        positionId = position.id,
                        positionName = position.name,
                    )
                applicants += created
                ok(created)
            }
    }

@Singleton
class FakeStockRepository
    @Inject
    constructor(
        private val backend: FakeBackend,
    ) : StockRepository {
        override suspend fun items(search: String): AppResult<Page<StockItem>> =
            backend.call {
                val q = search.trim().lowercase()
                if (q == FakeData.ERROR_TRIGGER) return@call fail(DomainError.Network())
                val items =
                    stock.filter {
                        q.isEmpty() || it.model.lowercase().contains(q) ||
                            it.brand
                                .orEmpty()
                                .lowercase()
                                .contains(q) ||
                            it.type
                                .orEmpty()
                                .lowercase()
                                .contains(q)
                    }
                ok(Page(items, 0, 1, items.size.toLong()))
            }
    }

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
private val PHONE = Regex("^\\(\\d{2}\\)\\d{4,5}-\\d{4}$")

/** Mesmas restrições do ApplicantRequestDTO. */
private fun validateApplicant(applicant: NewApplicant): DomainError? {
    val email = applicant.email?.trim().orEmpty()
    val phone = applicant.phone?.trim().orEmpty()
    return when {
        applicant.name.isBlank() -> {
            DomainError.Validation("Informe o nome do solicitante.", mapOf("name" to "Obrigatório"))
        }

        applicant.positionId.isBlank() -> {
            DomainError.Validation("Selecione o cargo.", mapOf("positionId" to "Obrigatório"))
        }

        email.isNotEmpty() && !EMAIL.matches(email) -> {
            DomainError.Validation(
                "Informe um e-mail válido.",
                mapOf("email" to "E-mail inválido."),
            )
        }

        phone.isNotEmpty() && !PHONE.matches(phone) -> {
            DomainError.Validation("Use (99)9999-9999 ou (99)99999-9999.", mapOf("phone" to "Formato inválido."))
        }

        else -> {
            null
        }
    }
}
