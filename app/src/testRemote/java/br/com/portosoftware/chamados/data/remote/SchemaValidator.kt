package br.com.portosoftware.chamados.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Validador de JSON Schema só com as palavras-chave que `contracts/openapi.yaml` usa. Palavra-chave que ele não conhece
 * derruba o teste, em vez de passar sem conferir: quem mudar o contrato estende o validador. Segue o JSON Schema onde
 * isso costuma enganar:
 * - `pattern` não tem âncora implícita, e o `$` casa só no fim do texto, como no ECMA-262, e não antes de um `\n` final,
 *   como no Java;
 * - tamanho conta code points, e cada regra só vale para o tipo dela;
 * - `date` e `date-time` seguem a forma do RFC 3339 (ano de 4 dígitos, segundos obrigatórios, offset em horas e minutos);
 * - número e booleano seguem a gramática do JSON: o parser do kotlinx entrega `01` e `TRUE` sem aspas como valores, e o
 *   validador os recusa.
 *
 * Onde ele difere do padrão, de propósito ou por limite:
 * - **Mais frouxo:** `email` aceita letra fora do ASCII na parte local, como o `@Email` do backend; o padrão a deixa
 *   para `idn-email`. `format: double` aceita qualquer número.
 * - **Mais estrito:** inteiro escrito `1.0` ou `1e2`; `enum` compara o texto do JSON, então `1` e `1.0` diferem;
 *   segundo 60 e fração com mais de 9 casas em `date-time`, que o `OffsetDateTime` recusa; as faixas de `int32` e
 *   `int64`.
 *
 * O `ContractFixturesTest` tem um controle que falha para cada palavra-chave e para cada item da primeira lista.
 */
internal object SchemaValidator {
    private val annotations = setOf("description", "title", "example", "examples", "deprecated", "readOnly", "writeOnly")
    private val keywords =
        setOf(
            "\$ref",
            "type",
            "enum",
            "oneOf",
            "not",
            "properties",
            "required",
            "additionalProperties",
            "propertyNames",
            "items",
            "minItems",
            "maxItems",
            "minLength",
            "maxLength",
            "pattern",
            "format",
            "minimum",
        )
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** Parte local e domínio sem ponto no início, no fim ou repetido, e domínio com ao menos um ponto. */
    private val email = Regex("""[^@\s.]+(\.[^@\s.]+)*@[^@\s.]+(\.[^@\s.]+)+""")
    private val date = Regex("""\d{4}-\d{2}-\d{2}""")
    private val dateTime = Regex("""\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}:\d{2}(\.\d+)?([Zz]|[+-]\d{2}:\d{2})""")
    private val integer = Regex("""-?(0|[1-9]\d*)""")
    private val number = Regex("""-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?""")

    /** Escape, classe de caracteres ou `$`: o que [ecma] percorre num `pattern`. */
    private val patternTokens = Regex("""\\.|\[(?:\\.|[^\\\]])*]|\$""")

    fun errors(
        schema: JsonElement,
        value: JsonElement,
    ): List<String> = mutableListOf<String>().also { check(schema.jsonObject, value, "$", it) }

    fun isValid(
        schema: JsonElement,
        value: JsonElement,
    ) = errors(schema, value).isEmpty()

    private fun check(
        schema: JsonObject,
        value: JsonElement,
        at: String,
        out: MutableList<String>,
    ) {
        val unknown = schema.keys - keywords - annotations
        require(unknown.isEmpty()) { "$at: palavra-chave sem suporte no validador: $unknown" }
        OpenApiContract.refName(schema)?.let { check(OpenApiContract.schema(it), value, at, out) }
        val types = schema["type"]?.let { type -> (type as? JsonArray ?: JsonArray(listOf(type))).map { it.jsonPrimitive.content } }
        if (types != null && types.none { hasType(value, it) }) {
            out += "$at: ${kindOf(value)} onde o schema pede $types"
        } else {
            checkCombinators(schema, value, at, out)
            checkByType(schema, value, at, out)
        }
    }

    private fun checkCombinators(
        schema: JsonObject,
        value: JsonElement,
        at: String,
        out: MutableList<String>,
    ) {
        schema["enum"]?.let { values -> if (value !in values.jsonArray) out += "$at: $value fora de $values" }
        schema["oneOf"]?.let { branches ->
            val matches = branches.jsonArray.count { isValid(it, value) }
            if (matches != 1) out += "$at: casa com $matches ramos de oneOf, e não com 1"
        }
        schema["not"]?.let { if (isValid(it, value)) out += "$at: casa com o schema de not" }
    }

    private fun checkByType(
        schema: JsonObject,
        value: JsonElement,
        at: String,
        out: MutableList<String>,
    ) {
        val primitive = value.scalar()
        when {
            value is JsonObject -> checkObject(schema, value, at, out)
            value is JsonArray -> checkArray(schema, value, at, out)
            primitive?.isString == true -> checkString(schema, primitive.content, at, out)
            primitive != null -> checkNumber(schema, primitive, at, out)
        }
    }

    private fun checkObject(
        schema: JsonObject,
        value: JsonObject,
        at: String,
        out: MutableList<String>,
    ) {
        val properties = schema["properties"]?.jsonObject.orEmpty()
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        required.filter { it !in value }.forEach { out += "$at.$it: obrigatória e ausente" }
        val additional = schema["additionalProperties"]
        value.forEach { (key, item) ->
            val property = properties[key]
            when {
                property != null -> check(property.jsonObject, item, "$at.$key", out)
                additional is JsonObject -> check(additional, item, "$at.$key", out)
                additional?.jsonPrimitive?.booleanOrNull == false -> out += "$at.$key: chave a mais"
            }
            schema["propertyNames"]?.let { check(it.jsonObject, JsonPrimitive(key), "$at[chave $key]", out) }
        }
    }

    private fun checkArray(
        schema: JsonObject,
        value: JsonArray,
        at: String,
        out: MutableList<String>,
    ) {
        schema["minItems"]?.let { if (value.size < it.jsonPrimitive.content.toInt()) out += "$at: menos itens que $it" }
        schema["maxItems"]?.let { if (value.size > it.jsonPrimitive.content.toInt()) out += "$at: mais itens que $it" }
        schema["items"]?.let { items -> value.forEachIndexed { index, item -> check(items.jsonObject, item, "$at[$index]", out) } }
    }

    private fun checkString(
        schema: JsonObject,
        value: String,
        at: String,
        out: MutableList<String>,
    ) {
        val length = value.codePointCount(0, value.length)
        schema["minLength"]?.let { if (length < it.jsonPrimitive.content.toInt()) out += "$at: mais curto que $it" }
        schema["maxLength"]?.let { if (length > it.jsonPrimitive.content.toInt()) out += "$at: mais longo que $it" }
        val pattern = schema["pattern"]?.jsonPrimitive?.content
        if (pattern != null && !ecma(pattern).containsMatchIn(value)) out += "$at: \"$value\" fora de $pattern"
        val format = schema["format"]?.jsonPrimitive?.content
        if (format != null && !hasStringFormat(value, format)) out += "$at: \"$value\" não é $format"
    }

    private fun checkNumber(
        schema: JsonObject,
        value: JsonPrimitive,
        at: String,
        out: MutableList<String>,
    ) {
        val number = value.content.toBigDecimalOrNull() ?: return
        schema["minimum"]?.let { if (number < BigDecimal(it.jsonPrimitive.content)) out += "$at: menor que $it" }
        val range =
            when (schema["format"]?.jsonPrimitive?.content) {
                "int32" -> Int.MIN_VALUE.toBigDecimal()..Int.MAX_VALUE.toBigDecimal()
                "int64" -> Long.MIN_VALUE.toBigDecimal()..Long.MAX_VALUE.toBigDecimal()
                else -> null
            }
        if (range != null && number !in range) out += "$at: $number fora de ${schema["format"]}"
    }

    private fun hasStringFormat(
        value: String,
        format: String,
    ): Boolean =
        when (format) {
            "uuid" -> uuid.matches(value)
            "email" -> email.matches(value)
            "date" -> date.matches(value) && runCatching { LocalDate.parse(value) }.isSuccess
            "date-time" -> dateTime.matches(value) && runCatching { OffsetDateTime.parse(value) }.isSuccess
            else -> error("formato de texto sem suporte no validador: $format")
        }

    private val typeChecks: Map<String, (JsonElement) -> Boolean> =
        mapOf(
            "null" to { it is JsonNull },
            "object" to { it is JsonObject },
            "array" to { it is JsonArray },
            "string" to { it.scalar()?.isString == true },
            "boolean" to { it.unquoted()?.content in setOf("true", "false") },
            "integer" to { it.unquoted()?.content?.let(integer::matches) == true },
            "number" to { it.unquoted()?.content?.let(number::matches) == true },
        )

    private fun hasType(
        value: JsonElement,
        type: String,
    ): Boolean = (typeChecks[type] ?: error("tipo sem suporte no validador: $type"))(value)

    /** O `pattern` com o `$` de fora das classes trocado por `\z`, o fim do texto. */
    private fun ecma(pattern: String) = Regex(patternTokens.replace(pattern) { if (it.value == "$") "\\z" else it.value })

    /** Texto, número ou booleano; `null` para nulo, objeto e lista. */
    private fun JsonElement.scalar(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

    private fun JsonElement.unquoted(): JsonPrimitive? = scalar()?.takeIf { !it.isString }

    private fun kindOf(value: JsonElement) =
        when {
            value is JsonNull -> "null"
            value is JsonObject -> "objeto"
            value is JsonArray -> "lista"
            value.jsonPrimitive.isString -> "texto"
            else -> "valor ${value.jsonPrimitive.content}"
        }
}
