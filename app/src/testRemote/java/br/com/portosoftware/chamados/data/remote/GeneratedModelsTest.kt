package br.com.portosoftware.chamados.data.remote

import br.com.portosoftware.chamados.contract.model.CallResponseDTO
import br.com.portosoftware.chamados.contract.model.CallSummaryResponseDTO
import br.com.portosoftware.chamados.contract.model.PagedModelCallSummaryResponseDTO
import br.com.portosoftware.chamados.contract.model.Result
import br.com.portosoftware.chamados.contract.model.SessionInfoResponseDTO
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smoke tests for the Kotlin models generated from the backend OpenAPI snapshot. */
@OptIn(ExperimentalSerializationApi::class)
class GeneratedModelsTest {
    private fun response(name: String): String = requireNotNull(javaClass.getResource("/api-responses/$name.json")).readText()

    private inline fun <reified T> read(name: String): T = ApiJson.decodeFromString(response(name))

    private fun responseObject(name: String) = ApiJson.parseToJsonElement(response(name)).jsonObject

    @Test
    fun `call model keeps ids dates nulls and enums`() {
        val call = read<CallResponseDTO>("call-full")
        assertEquals("1607ab39-e671-5ee5-bc94-bde22d2ab011", call.id)
        assertEquals("2026-02-01T05:00:00.000000", call.openingDate)
        assertEquals(CallResponseDTO.Status.OPENED, call.status)
        assertEquals(CallResponseDTO.Priority.NORMAL, call.priority)
        assertEquals(
            "a.txt",
            call.attachments
                .orEmpty()
                .first()
                .originalFileName,
        )
        assertNull(read<CallResponseDTO>("call-minimal").completionDate)
    }

    @Test
    fun `unknown enum values use generated fallback`() {
        val body =
            response("call-full")
                .replace("\"status\":\"OPENED\"", "\"status\":\"NEW\"")
                .replace("\"priority\":\"NORMAL\"", "\"priority\":\"CRITICAL\"")
        val call = ApiJson.decodeFromString<CallResponseDTO>(body)
        assertEquals(CallResponseDTO.Status.unknown_default_open_api, call.status)
        assertEquals(CallResponseDTO.Priority.unknown_default_open_api, call.priority)
    }

    @Test
    fun `unknown fields are ignored and absent nullable fields stay null`() {
        val withExtra = JsonObject(responseObject("call-full") + ("newField" to JsonPrimitive("ignored")))
        assertEquals("1607ab39-e671-5ee5-bc94-bde22d2ab011", ApiJson.decodeFromString<CallResponseDTO>(withExtra.toString()).id)

        val withoutDate = JsonObject(responseObject("call-minimal") - "openingDate")
        assertNull(ApiJson.decodeFromString<CallResponseDTO>(withoutDate.toString()).openingDate)
    }

    @Test
    fun `paged model preserves content and metadata`() {
        val page = read<PagedModelCallSummaryResponseDTO>("calls-filter")
        assertEquals(2, page.content.orEmpty().size)
        assertEquals(2L, page.page?.propertySize)
        assertEquals(
            CallSummaryResponseDTO.Status.IN_PROGRESS,
            page.content
                .orEmpty()
                .first()
                .status,
        )
    }

    @Test
    fun `dynamic report and session fields remain readable`() {
        val report = read<Result>("report-stock")
        assertTrue(report.generatedAt.orEmpty().endsWith("-03:00"))
        assertEquals(2, report.propertySize)
        assertEquals(JsonPrimitive(3), report.rows.orEmpty().single()["quantity"])
        assertEquals(JsonPrimitive("Marca"), report.rows.orEmpty().single()["brand"])

        val session = read<SessionInfoResponseDTO>("session")
        assertTrue(session.expiresAt.orEmpty().endsWith("Z"))
        assertEquals(1799L, session.expiresInSeconds)
    }
}
