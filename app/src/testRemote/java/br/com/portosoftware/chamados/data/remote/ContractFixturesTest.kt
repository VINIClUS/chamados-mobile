package br.com.portosoftware.chamados.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * As respostas de teste (`api-responses`, capturadas, e `api-fixtures`, escritas à mão) seguem o schema da rota em
 * `contracts/openapi.yaml`. O `ApiWireTest` confere do mesmo jeito cada resposta que serve.
 */
class ContractFixturesTest {
    private data class Route(
        val method: String,
        val path: String,
        val status: Int,
    )

    /** Rota e status de cada arquivo. Arquivo novo sem entrada aqui derruba o teste. */
    private val manifest =
        mapOf(
            "api-responses/brands.json" to Route("GET", "/brand/all", 200),
            "api-responses/call-full.json" to Route("GET", "/calls/{id}", 200),
            "api-responses/call-minimal.json" to Route("GET", "/calls/{id}", 200),
            "api-responses/calls-filter.json" to Route("GET", "/calls/filter", 200),
            "api-responses/not-found.json" to Route("GET", "/brand/{id}", 404),
            "api-responses/report-stock.json" to Route("GET", "/reports/{id}", 200),
            "api-responses/session.json" to Route("POST", "/auth/login", 200),
            "api-fixtures/csrf.json" to Route("GET", "/auth/csrf", 200),
            "api-fixtures/items.json" to Route("GET", "/items/all", 200),
            "api-fixtures/validation-error.json" to Route("POST", "/calls", 400),
        )

    private val resources = File("src/testRemote/resources")

    private fun parse(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `cada resposta de teste segue o schema da rota`() {
        val files = listOf("api-responses", "api-fixtures").flatMap { dir -> File(resources, dir).list().orEmpty().map { "$dir/$it" } }
        assertTrue("rode a partir do módulo app: ${resources.absoluteFile}", files.isNotEmpty())

        assertEquals(manifest.keys, files.toSet())
        manifest.forEach { (file, route) ->
            val schema = requireNotNull(OpenApiContract.responseSchema(route.method, route.path, route.status)) { "$route sem corpo" }
            assertEquals(file, emptyList<String>(), SchemaValidator.errors(schema, parse(File(resources, file).readText())))
        }
    }

    @Test
    fun `o caminho concreto casa com a rota de mais segmentos fixos`() {
        fun ref(name: String) = parse("""{"${'$'}ref":"#/components/schemas/$name"}""")

        assertEquals(ref("PagedModelCallSummaryResponseDTO"), OpenApiContract.responseSchema("GET", "/calls/filter", 200))
        assertEquals(ref("CallResponseDTO"), OpenApiContract.responseSchema("GET", "/calls/c1", 200))
        assertEquals(ref("ApiErrorResponse"), OpenApiContract.responseSchema("GET", "/calls/c1", 404))
        assertEquals(null, OpenApiContract.responseSchema("POST", "/auth/logout", 200))
        assertThrows(IllegalStateException::class.java) { OpenApiContract.responseSchema("GET", "/calls/c1", 418) }
    }

    @Test
    fun `o validador aceita e recusa cada palavra-chave do contrato, como o JSON Schema`() {
        val failures =
            controls.flatMap { (schema, valid, invalid) ->
                val parsed = parse(schema)
                val refused = SchemaValidator.errors(parsed, parse(valid))
                listOfNotNull(
                    "$schema recusou $valid: $refused".takeIf { refused.isNotEmpty() },
                    "$schema aceitou $invalid".takeIf { SchemaValidator.isValid(parsed, parse(invalid)) },
                )
            }

        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `palavra-chave que o validador nao conhece derruba o teste, em vez de passar sem conferir`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                SchemaValidator.errors(parse("""{"maxProperties":1}"""), JsonObject(emptyMap()))
            }

        assertTrue(error.message, error.message!!.contains("maxProperties"))
        assertFalse(SchemaValidator.isValid(parse("""{"type":"string"}"""), JsonPrimitive(1)))
    }

    private companion object {
        const val UUID = "11111111-1111-4111-8111-111111111111"

        /** Schema, um valor que ele aceita e um que ele recusa. */
        val controls =
            listOf(
                Triple("""{"type":"string"}""", "\"a\"", "1"),
                Triple("""{"type":["string","null"]}""", "null", "1"),
                Triple("""{"type":"integer"}""", "1", "1.5"),
                Triple("""{"type":"integer"}""", "1", "\"1\""),
                Triple("""{"type":"number"}""", "1.5", "\"1.5\""),
                Triple("""{"type":"boolean"}""", "true", "\"true\""),
                Triple("""{"type":"array"}""", "[]", "{}"),
                Triple("""{"type":"object"}""", "{}", "[]"),
                Triple("""{"type":"string","enum":["A"]}""", "\"A\"", "\"a\""),
                Triple("""{"oneOf":[{"type":"null"},{"type":"string"}]}""", "null", "1"),
                Triple("""{"oneOf":[{"type":"string"},{"type":"string","minLength":2}]}""", "\"a\"", "\"ab\""),
                Triple("""{"type":["object","null"],"not":{"type":"object"}}""", "null", "{}"),
                Triple("""{"type":"object","properties":{"a":{"type":"string"}}}""", """{"a":"x"}""", """{"a":1}"""),
                Triple("""{"type":"object","properties":{"a":{"type":"string"}},"required":["a"]}""", """{"a":"x"}""", "{}"),
                Triple("""{"type":"object","additionalProperties":{"type":"string"}}""", """{"k":"v"}""", """{"k":1}"""),
                Triple("""{"type":"object","additionalProperties":false}""", "{}", """{"k":1}"""),
                Triple("""{"type":"object","propertyNames":{"format":"uuid"}}""", """{"$UUID":1}""", """{"k":1}"""),
                Triple("""{"type":"array","items":{"type":"integer"}}""", "[1]", """["x"]"""),
                Triple("""{"type":"array","minItems":1}""", "[1]", "[]"),
                Triple("""{"type":"array","maxItems":1}""", "[1]", "[1,2]"),
                Triple("""{"type":"string","minLength":2}""", "\"ãé\"", "\"a\""),
                Triple("""{"type":"string","maxLength":2}""", "\"😀😀\"", "\"abc\""),
                Triple("""{"type":"string","pattern":"\\d{2}"}""", "\"a12b\"", "\"a1b\""),
                Triple("""{"type":"string","pattern":"(?i)^[a-z]${'$'}"}""", "\"A\"", "\"1\""),
                Triple("""{"type":"string","format":"uuid"}""", "\"$UUID\"", "\"1111\""),
                Triple("""{"type":"string","format":"date"}""", "\"2026-01-02\"", "\"2026-02-30\""),
                Triple("""{"type":"string","format":"date-time"}""", "\"2026-01-02T03:04:05Z\"", "\"2026-01-02T03:04:05\""),
                Triple("""{"type":"string","format":"email"}""", "\"a@chamados.test\"", "\"a\""),
                Triple("""{"type":"integer","format":"int32"}""", "2147483647", "2147483648"),
                Triple("""{"type":"integer","format":"int64"}""", "9223372036854775807", "9223372036854775808"),
                Triple("""{"type":"integer","minimum":0}""", "0", "-1"),
                Triple("""{"minLength":2}""", "1", "\"a\""),
                // Onde o Java ou o parser do kotlinx aceitam mais que o JSON Schema.
                Triple("""{"type":"string","pattern":"^\\d{2}${'$'}"}""", "\"12\"", "\"12\\n\""),
                Triple("""{"type":"string","pattern":"^[${'$'}]${'$'}"}""", "\"${'$'}\"", "\"${'$'}\\n\""),
                Triple("""{"type":"string","format":"date"}""", "\"2026-01-02\"", "\"+12026-01-02\""),
                Triple("""{"type":"string","format":"date"}""", "\"2026-01-02\"", "\"-0001-01-02\""),
                Triple("""{"type":"string","format":"date-time"}""", "\"2026-01-02T03:04:05.1-03:00\"", "\"2026-01-02T03:04Z\""),
                Triple("""{"type":"string","format":"date-time"}""", "\"2026-01-02T03:04:05Z\"", "\"2026-01-02T03:04:05+01:00:30\""),
                Triple("""{"type":"string","format":"date-time"}""", "\"2026-01-02T03:04:05Z\"", "\"+12026-01-02T03:04:05Z\""),
                Triple("""{"type":"string","format":"date-time"}""", "\"2026-01-02T03:04:05Z\"", "\"2026-01-02T03:04:05Z\\n\""),
                Triple("""{"type":"string","format":"date-time"}""", "\"2026-01-02T03:04:05Z\"", "\"2026-01-02 03:04:05Z\""),
                Triple("""{"type":"string","format":"email"}""", "\"ação@chamados.test\"", "\"a.@chamados.test\""),
                Triple("""{"type":"string","format":"email"}""", "\"a.b@chamados.test\"", "\"a@chamados..test\""),
                Triple("""{"type":"string","format":"email"}""", "\"a@chamados.test\"", "\"a@chamados\""),
                Triple("""{"type":"integer"}""", "-10", "01"),
                Triple("""{"type":"number"}""", "-0.5e2", "01.5"),
                Triple("""{"type":"boolean"}""", "false", "TRUE"),
                Triple("""{"type":"boolean"}""", "false", "True"),
                Triple(
                    """{"${'$'}ref":"#/components/schemas/PageMetadata"}""",
                    """{"number":0,"size":20,"totalElements":0}""",
                    """{"number":0,"size":"vinte","totalElements":0}""",
                ),
            )
    }
}
