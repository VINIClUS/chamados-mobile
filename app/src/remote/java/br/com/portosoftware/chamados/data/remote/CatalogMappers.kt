package br.com.portosoftware.chamados.data.remote

import br.com.portosoftware.chamados.contract.model.ApplicantResponseDTO
import br.com.portosoftware.chamados.contract.model.CategoryResponseDTO
import br.com.portosoftware.chamados.contract.model.EquipmentModelResponseDTO
import br.com.portosoftware.chamados.contract.model.EquipmentResponseDTO
import br.com.portosoftware.chamados.contract.model.ItemResponseDTO
import br.com.portosoftware.chamados.contract.model.LocalResponseDTO
import br.com.portosoftware.chamados.contract.model.PositionResponseDTO
import br.com.portosoftware.chamados.contract.model.SubCategoryResponseDTO
import br.com.portosoftware.chamados.contract.model.UnitResponseDTO
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.Equipment
import br.com.portosoftware.chamados.core.model.Local
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.Position
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.SubCategory
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun String?.required(field: String): String = this ?: error("Campo obrigatório ausente: $field")

internal fun ApplicantResponseDTO.toDomain() = Applicant(
    id = id.required("id"),
    name = name.required("name"),
    email = email,
    phone = phone,
    unitId = unitId,
    unitName = unitName,
    localId = localId,
    localName = localName,
    positionId = positionId,
    positionName = positionName,
)

internal fun PositionResponseDTO.toDomain() = Position(id.required("id"), name.required("name"))

private fun JsonElement?.namedValue(): String? = this?.jsonObject?.get("name")?.jsonPrimitive?.content

private fun EquipmentModelResponseDTO.brandName(): String? = brand.namedValue()

internal fun EquipmentResponseDTO.toDomain() = Equipment(
    id = id.required("id"),
    patrimony = patrimony,
    model = listOfNotNull(model?.equipmentType?.name, model?.brandName(), model?.name)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")
        .ifBlank { null },
)

internal fun UnitResponseDTO.toDomain() = OrgUnit(id.required("id"), name.required("name"))

internal fun LocalResponseDTO.toDomain() = Local(
    id = id.required("id"),
    name = name.required("name"),
    unitId = unit?.id ?: id.required("unit.id"),
)

internal fun CategoryResponseDTO.toDomain() = Category(id.required("id"), name.required("name"))

internal fun SubCategoryResponseDTO.toDomain() = SubCategory(
    id = id.required("id"),
    name = name.required("name"),
    categoryId = category?.id ?: error("Campo obrigatório ausente: category.id"),
)

internal fun ItemResponseDTO.toDomain() = StockItem(
    id = id.required("id"),
    model = model?.name ?: itemType?.name ?: "Item",
    brand = brand?.name ?: model?.brand,
    type = itemType?.name ?: model?.type,
    quantity = quantity ?: 0,
    consumable = consumable == true,
    patrimony = patrimony,
    serialNumber = serialNumber,
    specifications = specifications.orEmpty().mapNotNull { spec ->
        val value = spec.value ?: return@mapNotNull null
        val unit = spec.key?.unit?.takeUnless { value.contains(it, ignoreCase = true) }
        val label = spec.key?.displayName ?: spec.key?.keyName ?: "Especificação"
        label to listOfNotNull(value, unit).joinToString(" ")
    },
    isUnique = itemType?.isUnique == true,
)
