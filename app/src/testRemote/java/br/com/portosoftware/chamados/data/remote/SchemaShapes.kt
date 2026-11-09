package br.com.portosoftware.chamados.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON montado a partir de um schema de `contracts/openapi.yaml`, sem caso escrito à mão: um schema novo no contrato
 * ganha formas sozinho.
 * - **Mínima:** só as chaves obrigatórias, `null` onde o schema aceita, listas e mapas vazios.
 * - **Cheia:** todas as chaves, com valor onde o schema aceita algum, uma entrada em cada lista e mapa.
 *
 * Num ciclo de `$ref`, o schema que já está na pilha entra na forma mínima. Chave obrigatória nunca sai para fechar o
 * ciclo, porque a leitura falharia por isso.
 */
internal object SchemaShapes {
    const val UNKNOWN_ENUM = "VALOR_NOVO"

    private const val UUID = "11111111-1111-4111-8111-111111111111"

    /** Exemplos para os `pattern` do contrato; um `pattern` novo sem exemplo derruba o teste com o caminho. */
    private val patternSamples = listOf("2026-01-02T03:04:05.000000", "(48)99999-0000", "12.345.678/0001-90")

    /** Valor de uma chave sem tipo (linha de relatório): qualquer JSON vale. */
    private val anyValue = JsonObject(mapOf("a" to JsonArray(listOf(JsonPrimitive(1), JsonNull, JsonPrimitive("b")))))

    fun minimal(name: String): JsonElement = Builder(full = false, enumValue = null).ref(name, emptyList())

    /** [enumValue] substitui o valor de todo enum, inclusive os aninhados. */
    fun full(
        name: String,
        enumValue: String? = null,
    ): JsonElement = Builder(full = true, enumValue = enumValue).ref(name, emptyList())

    fun isNullable(schema: JsonObject): Boolean {
        val type = schema["type"]
        return (type is JsonArray && JsonPrimitive("null") in type) ||
            type?.jsonPrimitive?.content == "null" ||
            schema["oneOf"]?.jsonArray?.any { it.jsonObject["type"]?.jsonPrimitive?.content == "null" } == true
    }

    /** O schema sem a alternativa nula, ou `null` se a chave só aceita nulo. */
    fun nonNullBranch(schema: JsonObject): JsonObject? {
        val oneOf = schema["oneOf"]?.jsonArray
        val type = schema["type"]
        return when {
            oneOf != null -> oneOf.map { it.jsonObject }.single { it["type"]?.jsonPrimitive?.content != "null" }
            type is JsonArray -> withoutNull(schema, type)
            type?.jsonPrimitive?.content == "null" -> null
            else -> schema
        }
    }

    /** `type: [string, "null"]` vira `type: string`; a chave sempre nula (`not`) não tem ramo. */
    private fun withoutNull(
        schema: JsonObject,
        type: JsonArray,
    ): JsonObject? {
        val rest = type.map { it.jsonPrimitive.content } - "null"
        return if (rest.isEmpty() || "not" in schema) null else JsonObject(schema + ("type" to JsonPrimitive(rest.single())))
    }

    /** Tipo JSON do ramo não nulo, já seguindo o `$ref`: `null` para chave sem tipo, `enum` para enum. */
    fun kindOf(schema: JsonObject): String? {
        val branch = nonNullBranch(schema) ?: return "null"
        OpenApiContract.refName(branch)?.let { return kindOf(OpenApiContract.schema(it)) }
        return when {
            "enum" in branch -> "enum"
            "type" in branch -> branch.getValue("type").jsonPrimitive.content
            "properties" in branch -> "object"
            else -> null
        }
    }

    private class Builder(
        val full: Boolean,
        val enumValue: String?,
    ) {
        fun ref(
            name: String,
            stack: List<String>,
        ): JsonElement {
            if (name !in stack) return value(OpenApiContract.schema(name), stack + name)
            check(full) { "ciclo só de chaves obrigatórias: ${stack + name}" }
            return Builder(full = false, enumValue = enumValue).ref(name, emptyList())
        }

        fun value(
            schema: JsonObject,
            stack: List<String>,
        ): JsonElement {
            val branch = nonNullBranch(schema)
            return if (branch == null || (!full && isNullable(schema))) JsonNull else branchValue(branch, stack)
        }

        private fun branchValue(
            branch: JsonObject,
            stack: List<String>,
        ): JsonElement {
            val name = OpenApiContract.refName(branch)
            val values = branch["enum"]?.jsonArray
            return when {
                name != null -> ref(name, stack)
                values != null -> JsonPrimitive(enumValue ?: values.first().jsonPrimitive.content)
                else -> typedValue(branch, stack)
            }
        }

        private fun typedValue(
            branch: JsonObject,
            stack: List<String>,
        ): JsonElement =
            when (kindOf(branch)) {
                "object" -> objectValue(branch, stack)
                "array" -> arrayValue(branch, stack)
                "string" -> JsonPrimitive(stringValue(branch))
                "integer" -> JsonPrimitive(maxOf(1, branch["minimum"]?.jsonPrimitive?.content?.toInt() ?: 1))
                "number" -> JsonPrimitive(1.5)
                "boolean" -> JsonPrimitive(true)
                else -> anyValue
            }

        private fun objectValue(
            schema: JsonObject,
            stack: List<String>,
        ): JsonObject {
            val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val properties = schema["properties"]?.jsonObject.orEmpty().filterKeys { full || it in required }
            val values = properties.mapValues { (_, property) -> value(property.jsonObject, stack) }.toMutableMap()
            val additional = schema["additionalProperties"]
            if (full && additional is JsonObject) {
                val key = if (schema["propertyNames"]?.jsonObject?.get("format") != null) UUID else "chave"
                values[key] = value(additional, stack)
            }
            return JsonObject(values)
        }

        private fun arrayValue(
            schema: JsonObject,
            stack: List<String>,
        ): JsonArray {
            val minItems = schema["minItems"]?.jsonPrimitive?.content?.toInt() ?: 0
            val count = if (full) maxOf(1, minItems) else minItems
            return JsonArray(List(count) { value(schema.getValue("items").jsonObject, stack) })
        }

        private fun stringValue(schema: JsonObject): String {
            val pattern = schema["pattern"]?.jsonPrimitive?.content
            return when (schema["format"]?.jsonPrimitive?.content) {
                "uuid" -> {
                    UUID
                }

                "date" -> {
                    "2026-01-02"
                }

                "date-time" -> {
                    "2026-01-02T03:04:05Z"
                }

                "email" -> {
                    "a@chamados.test"
                }

                else -> {
                    if (pattern != null) {
                        patternSamples.firstOrNull { Regex(pattern).containsMatchIn(it) } ?: error("sem exemplo para o pattern $pattern")
                    } else {
                        "x".repeat(maxOf(1, schema["minLength"]?.jsonPrimitive?.content?.toInt() ?: 1))
                    }
                }
            }
        }
    }
}
