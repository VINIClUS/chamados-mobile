package br.com.portosoftware.chamados.data.remote

import br.com.portosoftware.chamados.contract.model.EquipmentResponseDTO
import br.com.portosoftware.chamados.contract.model.ItemResponseDTO
import br.com.portosoftware.chamados.core.model.StockItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Nomes, marcas e especificações recortados de respostas reais do backend com a carga inicial (`Init`). O resto de
 * cada objeto só completa as chaves que o contrato exige.
 */
class CatalogMappersTest {
    private fun equipment(
        type: String,
        brand: String,
        model: String,
        patrimony: String? = null,
    ) = ApiJson
        .decodeFromString<EquipmentResponseDTO>(
            """
            {"id":"e1","patrimony":${patrimony?.let { "\"$it\"" }},"serialNumber":null,"status":"ACTIVE","operatingSystem":null,
            "items":[],"specifications":[],"outsourcedRepairs":[],"local":$LOCAL,
            "model":{"id":"m1","name":"$model","brand":{"id":"b1","name":"$brand","equipmentModels":null,"itemModels":null},
            "equipmentType":{"id":"t1","name":"$type"}}}
            """.trimIndent(),
        ).toDomain()

    private fun item(
        brand: String,
        type: String,
        specifications: String,
        model: String? = null,
    ): StockItem {
        val modelJson =
            model?.let {
                """{"id":"m1","name":"$it","brand":"$brand","brandId":"b1","type":"$type","itemTypeId":"t1",
                |"category":"COMPONENT","ranking":0}
                """.trimMargin()
            }
        return ApiJson
            .decodeFromString<ItemResponseDTO>(
                """
                {"id":"i1","status":"STOCK","quantity":15,"consumable":false,"serialNumber":null,"patrimony":null,"local":null,
                "brand":{"id":"b1","name":"$brand","equipmentModels":null,"itemModels":null},
                "itemType":{"id":"t1","name":"$type","category":"COMPONENT","isUnique":false,"affectsRanking":true},
                "model":$modelJson,"specifications":$specifications}
                """.trimIndent(),
            ).toDomain()
    }

    private fun spec(
        displayName: String,
        unit: String?,
        value: String,
    ) = """{"id":"s1","value":"$value","key":{"id":"k1","keyName":"K","displayName":"$displayName","unit":${unit?.let { "\"$it\"" }},
        |"inputType":null,"specificationKeyCategory":null}}
        """.trimMargin()

    @Test
    fun `equipamento generico mostra o tipo e nao repete Generico`() {
        assertEquals("Computador Genérico(a)", equipment("Computador", "Genérico(a)", "Genérico(a)", patrimony = "12013").model)
    }

    @Test
    fun `equipamento de marca mostra tipo, marca e modelo`() {
        assertEquals("Monitor AOC E970SWNL", equipment("Monitor", "AOC", "E970SWNL").model)
    }

    @Test
    fun `unidade nao se repete quando o valor ja a traz`() {
        val specifications = "[${spec("Memória RAM", "GB", "8GB")},${spec("Potência", "W", "500")}]"

        val item = item("Genérico(a)", "Memória RAM", specifications)

        assertEquals(listOf("Memória RAM" to "8GB", "Potência" to "500 W"), item.specifications)
    }

    @Test
    fun `resumo do item distingue modelos genericos pelas especificacoes`() {
        val specifications = "[${spec("Memória RAM", "GB", "8GB")},${spec("Tipo de Memória", null, "DDR4")}]"

        assertEquals(
            "Genérico(a) · Memória RAM · 8GB · DDR4",
            item("Genérico(a)", "Memória RAM", specifications, model = "Genérico(a)").summary,
        )
    }

    private companion object {
        const val LOCAL =
            """{"id":"l1","name":"Local","receives":true,"unit":{"id":"u1","name":"Unidade",
            "secretary":{"id":"s1","name":"Secretaria","itTeam":{"id":"t1","name":"TI"}}}}"""
    }
}
