package br.com.portosoftware.chamados.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File

/**
 * `contracts/openapi.yaml` lido no teste, o mesmo arquivo de que o build gera os modelos. O caminho vem do Gradle
 * (`chamados.contract`), que também declara o arquivo como entrada da tarefa de teste: `pattern` e `format` não mudam o
 * Kotlin gerado, e sem isso uma mudança só neles deixaria o teste sem rodar.
 */
internal object OpenApiContract {
    private const val SCHEMA_PREFIX = "#/components/schemas/"
    private const val RESPONSE_PREFIX = "#/components/responses/"

    val file: File = File(System.getProperty("chamados.contract") ?: "contracts/backend/openapi.yaml")

    val root: JsonObject by lazy {
        check(file.isFile) { "contrato não encontrado em ${file.absoluteFile}: rode pelo Gradle, a partir do módulo app" }
        file.reader().use { toJson(Yaml(SafeConstructor(LoaderOptions())).load(it)) }.jsonObject
    }

    val schemas: JsonObject by lazy {
        root
            .getValue("components")
            .jsonObject
            .getValue("schemas")
            .jsonObject
    }

    /** Schemas que aparecem em corpo de requisição, com os que eles citam. Os demais só saem em resposta. */
    val requestSchemas: Set<String> by lazy {
        val bodies = paths.values.flatMap { it.values }.mapNotNull { it.jsonObject["requestBody"] }
        closure(bodies.flatMap { refsIn(it) }.toSet())
    }

    private val paths: Map<String, JsonObject> by lazy {
        root.getValue("paths").jsonObject.mapValues { (_, operations) ->
            JsonObject(operations.jsonObject.filterValues { it is JsonObject })
        }
    }

    fun schema(name: String): JsonObject = schemas[name]?.jsonObject ?: error("schema $name não está no contrato")

    fun refName(schema: JsonObject): String? = schema["\$ref"]?.jsonPrimitive?.content?.removePrefix(SCHEMA_PREFIX)

    /**
     * Schema da resposta de [method] em [path] (sem o prefixo `/api`) com [status]. O caminho concreto casa com o
     * modelo que tem mais segmentos fixos: `/calls/filter` é `/calls/filter`, e não `/calls/{id}`. Devolve `null`
     * quando o contrato declara a resposta sem corpo.
     */
    fun responseSchema(
        method: String,
        path: String,
        status: Int,
    ): JsonElement? {
        val template = templateFor(path) ?: error("$path não está no contrato")
        val operation = paths.getValue(template)[method.lowercase()]?.jsonObject ?: error("$method $template não está no contrato")
        val responses = operation.getValue("responses").jsonObject
        var response = responses["$status"]?.jsonObject ?: error("$method $template não declara $status")
        response["\$ref"]?.let { ref ->
            val name = ref.jsonPrimitive.content.removePrefix(RESPONSE_PREFIX)
            response =
                root
                    .getValue("components")
                    .jsonObject
                    .getValue("responses")
                    .jsonObject
                    .getValue(name)
                    .jsonObject
        }
        return response["content"]
            ?.jsonObject
            ?.get("application/json")
            ?.jsonObject
            ?.get("schema")
    }

    private fun templateFor(path: String): String? {
        val segments = path.trim('/').split('/')
        return paths.keys
            .filter { template ->
                val parts = template.trim('/').split('/')
                parts.size == segments.size && parts.indices.all { parts[it].startsWith("{") || parts[it] == segments[it] }
            }.maxByOrNull { template -> template.split('/').count { !it.startsWith("{") } }
    }

    private fun refsIn(element: JsonElement): List<String> =
        when (element) {
            is JsonObject -> {
                element.flatMap { (key, value) ->
                    if (key == "\$ref") listOfNotNull(refName(element)) else refsIn(value)
                }
            }

            is JsonArray -> {
                element.flatMap { refsIn(it) }
            }

            else -> {
                emptyList()
            }
        }

    private fun closure(start: Set<String>): Set<String> {
        val found = start.toMutableSet()
        val pending = ArrayDeque(start)
        while (pending.isNotEmpty()) {
            refsIn(schema(pending.removeFirst())).filter(found::add).forEach(pending::addLast)
        }
        return found
    }

    private fun toJson(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to toJson(item) })
            is List<*> -> JsonArray(value.map(::toJson))
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
}
