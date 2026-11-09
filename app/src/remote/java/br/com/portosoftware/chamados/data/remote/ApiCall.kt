package br.com.portosoftware.chamados.data.remote

import android.util.Log
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.DomainException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

/**
 * Leitura estrita: o backend manda toda chave de resposta, com `null` quando não há valor, então chave ausente derruba
 * a leitura em vez de virar `null` (um chamado sem `responsibleTechnician` ofereceria "Assumir"). Chave a mais é
 * ignorada, porque campo novo é mudança compatível. O `coerceInputValues` não tem alvo nos modelos gerados, e o
 * `GeneratedSchemasTest` falha se passar a ter. Na escrita, chave opcional sem valor fica fora do corpo, porque o
 * valor padrão dela é `null` e `encodeDefaults` é falso.
 */
internal val ApiJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

private const val TAG = "ApiCall"
private const val HTTP_UNPROCESSABLE_ENTITY = 422

/**
 * Converte exceções de rede/HTTP em [DomainError], lendo o `ApiErrorResponse` do backend.
 * É a fronteira com a rede: qualquer falha inesperada vira [DomainError.Unknown] (e vai para o log)
 * em vez de derrubar o app. Um [DomainException] do bloco já traz o erro, como a recusa de um anexo grande antes do envio.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: DomainException) {
        AppResult.Failure(e.error)
    } catch (e: HttpException) {
        AppResult.Failure(e.toDomainError())
    } catch (e: IOException) {
        Log.w(TAG, "Falha de rede", e)
        AppResult.Failure(DomainError.Network())
    } catch (e: Exception) {
        Log.e(TAG, "Falha inesperada na chamada à API", e)
        AppResult.Failure(DomainError.Unknown())
    }

/**
 * Endpoints que devolvem `Response<T>` não lançam [HttpException] sozinhos: sem isto,
 * um 403/500 viraria lista vazia. 204 (sem corpo) continua sendo lista vazia.
 */
internal fun <T> Response<List<T>>.bodyOrThrow(): List<T> = if (isSuccessful) body().orEmpty() else throw HttpException(this)

/** Mesmo que [bodyOrThrow] para endpoints sem corpo útil. */
internal fun Response<Unit>.successOrThrow() {
    if (!isSuccessful) throw HttpException(this)
}

internal fun HttpException.toDomainError(): DomainError {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val api = body?.let(::parseApiError)
    val message = api?.first
    val violations = api?.second.orEmpty()
    return when (code()) {
        HTTP_BAD_REQUEST, HTTP_CONFLICT, HTTP_UNPROCESSABLE_ENTITY -> {
            DomainError.Validation(
                message = message ?: "Revise os campos destacados.",
                fieldErrors = violations,
            )
        }

        HTTP_UNAUTHORIZED -> {
            DomainError.Unauthorized()
        }

        HTTP_FORBIDDEN -> {
            DomainError.Forbidden(message ?: DomainError.Forbidden().message)
        }

        HTTP_NOT_FOUND -> {
            DomainError.NotFound(message ?: DomainError.NotFound().message)
        }

        else -> {
            DomainError.Unknown(message ?: DomainError.Unknown().message)
        }
    }
}

private fun parseApiError(body: String): Pair<String?, Map<String, String>>? = runCatching {
    val json = ApiJson.parseToJsonElement(body).jsonObject
    val status = json["status"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
    if (status !in 400..599) return null
    val error = json["error"] as? JsonPrimitive ?: return null
    val message = json["message"] as? JsonPrimitive ?: return null
    val violationObject = json["violations"] as? JsonObject ?: return null
    if (violationObject.values.any { (it as? JsonPrimitive)?.isString != true }) return null
    val violations = violationObject.mapValues { (_, value) -> value.jsonPrimitive.content }
    error.content
    message.content.takeIf { it.isNotBlank() } to violations
}.getOrNull()
