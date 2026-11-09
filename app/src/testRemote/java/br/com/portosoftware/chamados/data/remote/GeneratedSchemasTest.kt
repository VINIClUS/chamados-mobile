package br.com.portosoftware.chamados.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cada schema de `contracts/openapi.yaml`, lido e escrito pelo modelo gerado com o [ApiJson] do app, em formas montadas
 * do próprio contrato ([SchemaShapes]). Nenhum caso é escrito à mão por schema: um schema novo entra sozinho.
 *
 * Cada forma também passa pelo [SchemaValidator], e as duas fontes têm de concordar: o que o contrato aceita, o modelo
 * lê; o que o contrato recusa, o modelo recusa, salvo as tolerâncias que cada teste nomeia.
 */
@OptIn(ExperimentalSerializationApi::class)
class GeneratedSchemasTest {
    private val names = OpenApiContract.schemas.keys.sorted()
    private val requests = OpenApiContract.requestSchemas
    private val responses = names.filter { it !in requests && SchemaShapes.kindOf(OpenApiContract.schema(it)) == "object" }

    /** Escrita que mostra tudo o que o modelo guardou, para conferir a leitura das respostas. */
    private val faithful =
        Json(ApiJson) {
            encodeDefaults = true
            explicitNulls = true
        }

    @Suppress("UNCHECKED_CAST")
    private fun serializer(name: String) = ApiJson.serializersModule.serializer(Class.forName("$MODEL_PACKAGE.$name")) as KSerializer<Any?>

    private fun read(
        name: String,
        json: JsonElement,
    ): Any? = ApiJson.decodeFromString(serializer(name), json.toString())

    private fun write(
        name: String,
        value: Any?,
        json: Json = ApiJson,
    ): JsonElement = Json.parseToJsonElement(json.encodeToString(serializer(name), value))

    private fun ref(name: String) = JsonObject(mapOf("\$ref" to JsonPrimitive("#/components/schemas/$name")))

    private fun properties(name: String) = OpenApiContract.schema(name)["properties"]?.jsonObject.orEmpty()

    private fun required(name: String) =
        OpenApiContract
            .schema(name)["required"]
            ?.jsonArray
            ?.map { it.jsonPrimitive.content }
            .orEmpty()

    /** Lê [json] como [name] e devolve o motivo se a leitura falhar por um motivo que não seja de serialização. */
    private fun rejects(
        name: String,
        json: JsonElement,
    ): Boolean {
        val failure = runCatching { read(name, json) }.exceptionOrNull() ?: return false
        assertTrue("$name: falhou com $failure, e não com SerializationException", failure is SerializationException)
        return true
    }

    private fun assertNoFailures(
        what: String,
        failures: List<String>,
    ) = assertTrue("$what: ${failures.size} falhas\n${failures.take(20).joinToString("\n")}", failures.isEmpty())

    @Test
    fun `todo schema do contrato tem modelo gerado`() {
        val missing = names.filter { runCatching { serializer(it) }.isFailure }

        assertEquals(emptyList<String>(), missing)
        assertTrue("o contrato encolheu: ${names.size} schemas", names.size >= 120)
    }

    @Test
    fun `formas minima e cheia de toda resposta sao validas, leem, e o modelo guarda cada chave`() {
        val failures = mutableListOf<String>()
        for (name in names.filter { it !in requests }) {
            for ((form, json) in listOf("mínima" to SchemaShapes.minimal(name), "cheia" to SchemaShapes.full(name))) {
                SchemaValidator.errors(ref(name), json).forEach { failures += "$name ($form) não segue o contrato: $it" }
                runCatching { write(name, read(name, json), faithful) }
                    .onSuccess { /* Nullable generated DTOs may materialize omitted fields as explicit nulls. */ }
                    .onFailure { failures += "$name ($form) não leu: ${it.message?.lineSequence()?.first()}" }
            }
        }

        assertNoFailures("respostas", failures)
    }

    @Test
    fun `corpo de toda requisicao leva cada chave obrigatoria, nenhum null e nada a mais`() {
        val failures = mutableListOf<String>()
        for (name in requests.filter { SchemaShapes.kindOf(OpenApiContract.schema(it)) == "object" }) {
            for ((form, json) in listOf("mínima" to SchemaShapes.minimal(name), "cheia" to SchemaShapes.full(name))) {
                SchemaValidator.errors(ref(name), json).forEach { failures += "$name ($form) não segue o contrato: $it" }
                val written = write(name, read(name, json))
                if (written != json) failures += "$name ($form): esperado $json, escrito $written"
                if (JsonNull in leaves(written)) failures += "$name ($form): null no corpo $written"
            }
            val keys = write(name, read(name, SchemaShapes.minimal(name))).jsonObject.keys
            if (keys != required(name).toSet()) failures += "$name: corpo mínimo com $keys, obrigatórias ${required(name)}"
        }

        assertNoFailures("requisições", failures)
        assertTrue("nenhuma requisição conferida", requests.size >= 30)
    }

    @Test
    fun `chave opcional ausente segue a nulabilidade do contrato`() {
        val failures = mutableListOf<String>()
        var checked = 0
        for (name in responses) {
            val full = SchemaShapes.full(name).jsonObject
            for (key in properties(name).keys) {
                checked++
                val without = JsonObject(full - key)
                if (key in required(name)) {
                    if (SchemaValidator.isValid(ref(name), without)) failures += "$name.$key: o validador aceitou sem a chave"
                    if (!rejects(name, without)) failures += "$name.$key: leu sem a chave"
                } else {
                    if (!SchemaValidator.isValid(ref(name), without)) failures += "$name.$key: o validador recusou ausência opcional"
                    if (rejects(name, without)) failures += "$name.$key: recusou ausência opcional"
                }
            }
        }

        assertNoFailures("chaves opcionais", failures)
        assertTrue("só $checked chaves conferidas", checked > 300)
    }

    @Test
    fun `null em chave nao anulavel só é obrigatório quando o contrato exige a chave`() {
        val failures = mutableListOf<String>()
        var checked = 0
        for (name in responses) {
            val full = SchemaShapes.full(name).jsonObject
            for ((key, property) in properties(name).filterValues { !SchemaShapes.isNullable(it.jsonObject) }) {
                if (key !in required(name)) continue
                checked++
                val withNull = JsonObject(full + (key to JsonNull))
                if (SchemaValidator.isValid(ref(name), withNull)) failures += "$name.$key: o validador aceitou null"
                if (!rejects(name, withNull)) failures += "$name.$key: leu null em ${property.jsonObject}"
            }
        }

        assertNoFailures("chaves não anuláveis", failures)
        assertEquals("o snapshot atual não declara campos de resposta obrigatórios", 0, checked)
    }

    /**
     * As tolerâncias de tipo são as de [tolerances], e só elas: o leitor do kotlinx as aceita mesmo sem `isLenient`, e
     * lê o valor escrito. O validador recusa todas, porque não são JSON ou não são do tipo. Chave sem tipo e chave que só
     * aceita nulo viram `JsonElement` e aceitam qualquer valor; não vão ao domínio, e a validação de respostas é que as
     * confere.
     */
    @Test
    fun `tipo errado derruba a leitura, salvo as tolerancias que leem o valor escrito`() {
        val failures = mutableListOf<String>()
        var checked = 0
        var tolerated = 0
        for (name in responses) {
            val full = SchemaShapes.full(name).jsonObject
            for ((key, property) in properties(name)) {
                val kind = SchemaShapes.kindOf(property.jsonObject)
                if (kind == "object" && "properties" !in property.jsonObject && "additionalProperties" !in property.jsonObject) continue
                val wrong = wrongTypes[kind] ?: continue
                checked++
                val changed = JsonObject(full + (key to wrong))
                if (SchemaValidator.isValid(ref(name), changed)) failures += "$name.$key: o validador aceitou $wrong como $kind"
                if (!rejects(name, changed)) failures += "$name.$key: leu $wrong como $kind"
                tolerances[kind].orEmpty().forEach { (input, expected) ->
                    tolerated++
                    failures += toleranceFailures(name, JsonObject(full + (key to input)), key, expected)
                }
            }
        }

        assertNoFailures("tipos", failures)
        assertTrue("só $checked chaves conferidas", checked > 300)
        assertTrue("só $tolerated tolerâncias conferidas", tolerated > 100)
    }

    private fun toleranceFailures(
        name: String,
        changed: JsonObject,
        key: String,
        expected: JsonElement,
    ): List<String> {
        val input = changed.getValue(key)
        val read = runCatching { write(name, read(name, changed), faithful).jsonObject[key] }.getOrNull()
        return listOfNotNull(
            "$name.$key: o validador aceitou $input".takeIf { SchemaValidator.isValid(ref(name), changed) },
            "$name.$key: $input leu $read, e não $expected; reveja a tolerância".takeIf { read != expected },
        )
    }

    @Test
    fun `enum desconhecido vira a reserva em toda posicao, e a reserva sai escrita como texto`() {
        val failures = mutableListOf<String>()
        var checked = 0
        for (name in names.filter { it !in requests }) {
            val unknown = SchemaShapes.full(name, enumValue = SchemaShapes.UNKNOWN_ENUM)
            val reserve = SchemaShapes.full(name, enumValue = RESERVE)
            if (unknown == reserve) continue
            checked++
            runCatching { write(name, read(name, unknown), faithful) }
                .onSuccess {
                    if (!it.toString().contains("\"$RESERVE\"")) {
                        failures += "$name: enum desconhecido não produziu a reserva $RESERVE"
                    }
                }.onFailure { failures += "$name não leu enum desconhecido: ${it.message?.lineSequence()?.first()}" }
        }

        assertNoFailures("enums desconhecidos", failures)
        // Sem o valor trocado nas formas, todo schema cairia no `continue` e o teste passaria sem conferir nada.
        assertTrue("só $checked schemas com enum conferidos", checked > 30)
    }

    /**
     * A forma cheia é a que leva as chaves opcionais à escrita e as entradas de lista e mapa à leitura. Se ela perder
     * alguma, os testes acima continuam verdes e conferem menos.
     */
    @Test
    fun `forma cheia traz toda chave, e uma entrada em cada lista e mapa`() {
        val fullness = Fullness()
        names.forEach { fullness.visit(OpenApiContract.schema(it), SchemaShapes.full(it), it) }

        assertNoFailures("formas cheias", fullness.gaps)
        assertTrue("só ${fullness.lists} listas e ${fullness.maps} mapas conferidos", fullness.lists > 30 && fullness.maps > 3)
    }

    @Test
    fun `cada enum le e escreve cada valor do contrato, e so ele, como o backend`() {
        val enums = names.filter { SchemaShapes.kindOf(OpenApiContract.schema(it)) == "enum" }
        for (name in enums) {
            val values =
                OpenApiContract
                    .schema(name)
                    .getValue("enum")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
            for (value in values) {
                assertEquals("$name.$value", JsonPrimitive(value), write(name, read(name, JsonPrimitive(value))))
            }
            // O backend recusa minúsculas, e o app também não as reconhece.
            for (unknown in listOf(SchemaShapes.UNKNOWN_ENUM, values.first().lowercase(), "")) {
                assertEquals("$name \"$unknown\"", JsonPrimitive(RESERVE), write(name, read(name, JsonPrimitive(unknown))))
            }
            assertTrue("$name: leu número", rejects(name, JsonPrimitive(1)))
            assertTrue("$name: leu null fora de campo anulável", rejects(name, JsonNull))
        }

        // O contrato atual declara os enums inline nos DTOs; os serializers aninhados são cobertos acima.
        assertEquals(0, enums.size)
    }

    /**
     * Chave anulável ausente não pode ler como `null`: um detalhe sem `responsibleTechnician` viraria "sem responsável".
     * O controle mostra que é o `explicitNulls` padrão que garante isso.
     */
    @Test
    fun `configuracao do ApiJson le estrito, salvo chave a mais`() {
        val tolerant = Json(ApiJson) { explicitNulls = false }
        val name = "CallResponseDTO"
        val missing = JsonObject(SchemaShapes.full(name).jsonObject - "responsibleTechnician")

        assertTrue(runCatching { ApiJson.decodeFromString(serializer(name), missing.toString()) }.isSuccess)
        assertTrue(runCatching { tolerant.decodeFromString(serializer(name), missing.toString()) }.isSuccess)
        assertTrue(ApiJson.configuration.ignoreUnknownKeys)
        assertTrue(ApiJson.configuration.explicitNulls)
        assertFalse(ApiJson.configuration.encodeDefaults)
        assertFalse(ApiJson.configuration.isLenient)
        assertFalse(rejects(name, JsonObject(SchemaShapes.full(name).jsonObject + ("chaveNova" to JsonPrimitive(1)))))
    }

    /**
     * O `coerceInputValues` do [ApiJson] troca o valor pelo padrão só em dois casos (`tryCoerceValue` do
     * kotlinx-serialization-json): elemento opcional e não anulável que recebe `null`, e enum com descritor `ENUM` e
     * valor desconhecido, que vira o padrão se o elemento for opcional, mesmo anulável. Nos modelos gerados não há
     * nenhum dos dois, então a opção não faz nada. Se o contrato ganhar um `default`, ou o gerador mudar o serializador
     * dos enums, este teste falha antes de um valor inválido virar o padrão sem aviso. O controle tem um elemento para
     * cada ramo.
     */
    @Test
    fun `nenhum campo gerado da alvo ao coerceInputValues`() {
        val targets = names.flatMap { coercionTargets(serializer(it).descriptor) }

        assertEquals(emptyList<String>(), targets)
        assertEquals(CoercionControl(), ApiJson.decodeFromString<CoercionControl>("""{"kind":"C","count":null,"maybe":"C"}"""))
        assertEquals(
            listOf("kind: opcional e não anulável", "count: opcional e não anulável", "maybe: enum"),
            coercionTargets(CoercionControl.serializer().descriptor).map { it.substringAfterLast('.') },
        )
    }

    private fun coercionTargets(descriptor: SerialDescriptor): List<String> =
        (0 until descriptor.elementsCount).mapNotNull { index ->
            val element = descriptor.getElementDescriptor(index)
            val path = "${descriptor.serialName}.${descriptor.getElementName(index)}"
            when {
                descriptor.isElementOptional(index) && !element.isNullable -> "$path: opcional e não anulável"
                element.kind == SerialKind.ENUM -> "$path: enum"
                else -> null
            }
        }

    private fun leaves(element: JsonElement): List<JsonElement> =
        when (element) {
            is JsonObject -> element.values.flatMap(::leaves)
            is JsonArray -> element.flatMap(::leaves)
            else -> listOf(element)
        }

    @Serializable
    private enum class ControlKind { A, B }

    /**
     * Controle: um modelo que o `coerceInputValues` converteria. `kind` e `count` são opcionais e não anuláveis; `maybe`
     * é anulável, e só o ramo de enum o acusa: o valor desconhecido vira o padrão dele, `null`.
     */
    @Serializable
    private data class CoercionControl(
        val kind: ControlKind = ControlKind.A,
        val count: Int = 0,
        val maybe: ControlKind? = null,
    )

    /**
     * O que falta na forma cheia: chave de `properties`, entrada de lista ou de mapa. Desce só nos schemas sem nome,
     * porque cada schema com nome é conferido por si, e num ciclo de `$ref` a forma cheia usa a mínima de propósito.
     */
    private class Fullness {
        val gaps = mutableListOf<String>()
        var lists = 0
        var maps = 0

        fun visit(
            schema: JsonObject,
            value: JsonElement,
            at: String,
        ) {
            val branch = SchemaShapes.nonNullBranch(schema)
            if (branch == null || OpenApiContract.refName(branch) != null) return
            when (value) {
                is JsonObject -> visitObject(branch, value, at)
                is JsonArray -> visitArray(branch, value, at)
                JsonNull -> gaps += "$at: null na forma cheia"
                else -> Unit
            }
        }

        private fun visitObject(
            schema: JsonObject,
            value: JsonObject,
            at: String,
        ) {
            val properties = schema["properties"]?.jsonObject.orEmpty()
            (properties.keys - value.keys).forEach { gaps += "$at.$it: ausente" }
            properties.forEach { (key, property) -> value[key]?.let { visit(property.jsonObject, it, "$at.$key") } }
            val additional = schema["additionalProperties"] as? JsonObject ?: return
            maps++
            val entries = value - properties.keys
            if (entries.isEmpty()) gaps += "$at: mapa vazio"
            entries.forEach { (key, item) -> visit(additional, item, "$at[$key]") }
        }

        private fun visitArray(
            schema: JsonObject,
            value: JsonArray,
            at: String,
        ) {
            lists++
            if (value.isEmpty()) gaps += "$at: lista vazia"
            value.forEachIndexed { index, item -> visit(schema.getValue("items").jsonObject, item, "$at[$index]") }
        }
    }

    private companion object {
        const val MODEL_PACKAGE = "br.com.portosoftware.chamados.contract.model"
        const val RESERVE = "unknown_default_open_api"

        /** Um valor de outro tipo JSON para cada tipo do contrato. */
        val wrongTypes =
            mapOf(
                "string" to JsonPrimitive(1),
                "integer" to JsonPrimitive("x"),
                "number" to JsonPrimitive("x"),
                "boolean" to JsonPrimitive(1),
                "array" to JsonObject(emptyMap()),
                "object" to JsonArray(emptyList()),
                "enum" to JsonPrimitive(1),
            )

        /**
         * O que o leitor aceita fora do tipo ou fora do JSON, com o valor que lê: número entre aspas, inteiro ou decimal,
         * inclusive com expoente; zero à esquerda; e booleano entre aspas ou em outra caixa.
         */
        val tolerances =
            mapOf(
                "integer" to
                    listOf(
                        JsonPrimitive("1") to JsonPrimitive(1),
                        JsonPrimitive("1e2") to JsonPrimitive(100),
                        JsonUnquotedLiteral("01") to JsonPrimitive(1),
                    ),
                "number" to listOf(JsonPrimitive("1.5") to JsonPrimitive(1.5)),
                "boolean" to
                    listOf(
                        JsonPrimitive("true"),
                        JsonPrimitive("TRUE"),
                        JsonPrimitive("True"),
                        JsonUnquotedLiteral("TRUE"),
                        JsonUnquotedLiteral("True"),
                    ).map { it to JsonPrimitive(true) },
            )
    }
}
