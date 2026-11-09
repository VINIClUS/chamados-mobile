package br.com.portosoftware.chamados.data.remote

import br.com.portosoftware.chamados.contract.model.CallResponseDTO
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.DomainError
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Erros HTTP para [DomainError]. Os corpos e as mensagens são os que o backend escreve (`GlobalExceptionHandler`,
 * `ApiErrors` e `SecurityConfig`); o formato deles está preso lá, em `ApiErrorSchemaTest` e `ErrorDispatchSecurityTest`.
 */
class ApiCallTest {
    private fun errorResponse(
        code: Int,
        body: String,
    ): Response<List<String>> = Response.error(code, body.toResponseBody("application/json".toMediaType()))

    private suspend fun errorFor(
        code: Int,
        body: String,
    ) = apiCall { throw HttpException(errorResponse(code, body)) }.error()

    /** Corpo cuja leitura cai no meio, como uma conexão que fecha. */
    private val failingBody =
        object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()

            override fun contentLength() = -1L

            override fun source(): BufferedSource =
                object : Source {
                    override fun read(
                        sink: Buffer,
                        byteCount: Long,
                    ): Long = throw IOException("conexão fechada")

                    override fun timeout() = Timeout.NONE

                    override fun close() = Unit
                }.buffer()
        }

    private fun <T> AppResult<T>.error() = (this as AppResult.Failure).error

    /** `ApiErrorResponse` como o backend responde: as seis chaves sempre vêm. */
    private fun apiError(
        status: Int,
        message: String,
        violations: String = "{}",
    ) = """{"timestamp":"2026-11-07T20:12:31.886942735Z","status":$status,"error":"Erro","message":"$message",
        |"path":"/api/calls","violations":$violations}
        """.trimMargin()

    @Test
    fun `violations do backend chegam como erros de campo`() =
        runTest {
            val body = apiError(400, "Dados inválidos", """{"description":"Muito curta","localId":"Obrigatório"}""")

            val error = apiCall { throw HttpException(errorResponse(400, body)) }.error()

            assertEquals(
                DomainError.Validation("Dados inválidos", mapOf("description" to "Muito curta", "localId" to "Obrigatório")),
                error,
            )
        }

    @Test
    fun `violations vazio continua sendo erro de validacao com a mensagem do backend`() =
        runTest {
            val error = apiCall { throw HttpException(errorResponse(409, apiError(409, "Saldo insuficiente"))) }.error()

            assertEquals(DomainError.Validation("Saldo insuficiente", emptyMap()), error)
        }

    @Test
    fun `lista com erro HTTP vira falha e nao lista vazia`() =
        runTest {
            val forbidden = apiCall { errorResponse(403, apiError(403, "Sem acesso")).bodyOrThrow() }.error()
            val serverError = apiCall { errorResponse(500, "").bodyOrThrow() }.error()

            assertEquals(DomainError.Forbidden("Sem acesso"), forbidden)
            assertTrue(serverError is DomainError.Unknown)
        }

    @Test
    fun `erros fora do formulario mantem a mensagem do backend`() =
        runTest {
            listOf(405, 406, 413, 415).forEach { code ->
                val body = apiError(code, "Motivo do backend")

                val error = apiCall { throw HttpException(errorResponse(code, body)) }.error()

                assertEquals("codigo $code", "Motivo do backend", error.message)
            }
        }

    @Test
    fun `corpo que nao e JSON, como o do nginx, nao derruba o tratamento de erro`() =
        runTest {
            val error = apiCall { throw HttpException(errorResponse(413, "<html>Too Large</html>")) }.error()

            assertTrue(error is DomainError.Unknown)
        }

    @Test
    fun `corpo de erro em JSON fora do contrato fica com a mensagem padrao`() =
        runTest {
            // Sem as chaves que o contrato exige, o corpo não é lido: nem a mensagem, nem os erros de campo.
            val forbidden = apiCall { throw HttpException(errorResponse(403, """{"message":"Sem acesso"}""")) }.error()
            val partial = """{"message":"Dados inválidos","violations":{"description":"Muito curta"}}"""
            val validation = apiCall { throw HttpException(errorResponse(400, partial)) }.error()

            assertEquals(DomainError.Forbidden(), forbidden)
            assertEquals(DomainError.Validation("Revise os campos destacados."), validation)
        }

    @Test
    fun `204 sem corpo continua sendo lista vazia`() =
        runTest {
            val result = apiCall { Response.success<List<String>>(204, null).bodyOrThrow() }

            assertEquals(AppResult.Success(emptyList<String>()), result)
        }

    @Test
    fun `cada status que o backend emite vira o erro de dominio, com a mensagem dele`() =
        runTest {
            val integrity = "A operação não pode ser concluída porque o registro está em uso ou já existe."
            val tooLarge = "O conteúdo enviado excede o limite permitido."
            val unsupported = "O formato do conteúdo enviado não é suportado."
            val notAcceptable = "O formato de resposta solicitado não é suportado."
            val cases =
                mapOf(
                    401 to DomainError.Unauthorized(),
                    403 to DomainError.Forbidden("Acesso negado."),
                    404 to DomainError.NotFound("Chamado não encontrado."),
                    405 to DomainError.Unknown("Método não permitido para este recurso."),
                    406 to DomainError.Unknown(notAcceptable),
                    409 to DomainError.Validation(integrity),
                    413 to DomainError.Unknown(tooLarge),
                    415 to DomainError.Unknown(unsupported),
                    // O backend não emite 422; o app o trata como validação, igual a 400 e 409.
                    422 to DomainError.Validation("Dados inválidos."),
                    500 to DomainError.Unknown("Ocorreu um erro interno inesperado."),
                )

            cases.forEach { (code, expected) ->
                // No 401, o app não mostra a mensagem do backend: renova a sessão ou pede o login.
                val message = if (code == 401) "Autenticação necessária." else expected.message
                assertEquals("codigo $code", expected, errorFor(code, apiError(code, message)))
            }
            val invalid = apiError(400, "Existem campos inválidos.", """{"localId":"O local é obrigatório."}""")
            assertEquals(
                DomainError.Validation("Existem campos inválidos.", mapOf("localId" to "O local é obrigatório.")),
                errorFor(400, invalid),
            )
        }

    @Test
    fun `resposta do proxy ou do filtro de CORS, que nao e JSON, fica com a mensagem padrao do status`() =
        runTest {
            assertEquals(DomainError.Unknown(), errorFor(413, "<html>413 Request Entity Too Large</html>"))
            assertEquals(DomainError.Unknown(), errorFor(502, "<html>502 Bad Gateway</html>"))
            assertEquals(DomainError.Unknown(), errorFor(504, ""))
            assertEquals(DomainError.Forbidden(), errorFor(403, "Invalid CORS request"))
        }

    @Test
    fun `corpo de erro com tipo errado ou null em violations nao e lido pela metade`() =
        runTest {
            val nullViolation = apiError(400, "Existem campos inválidos.", """{"localId":null}""")
            val listViolations = apiError(400, "Existem campos inválidos.", """["localId"]""")
            val numericMessage = apiError(404, "x").replace("\"x\"", "404")

            assertEquals(DomainError.Validation("Revise os campos destacados."), errorFor(400, nullViolation))
            assertEquals(DomainError.Validation("Revise os campos destacados."), errorFor(400, listViolations))
            assertEquals(DomainError.NotFound(), errorFor(404, numericMessage))
        }

    @Test
    fun `falha de rede vira Network, e falha ao ler o corpo de erro fica com o status`() =
        runTest {
            val network = apiCall { throw IOException("sem rota") }.error()
            val unreadable = apiCall { throw HttpException(Response.error<List<String>>(404, failingBody)) }.error()

            assertEquals(DomainError.Network(), network)
            assertEquals(DomainError.NotFound(), unreadable)
        }

    @Test
    fun `corpo de sucesso malformado vira erro generico, e nao rede`() =
        runTest {
            val malformed = apiCall { ApiJson.decodeFromString<CallResponseDTO>("{\"id\":") }.error()

            assertEquals(DomainError.Unknown(), malformed)
        }
}
