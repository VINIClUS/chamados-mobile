package br.com.portosoftware.chamados.core.model

import br.com.portosoftware.chamados.core.model.CallStatus.CLOSED
import br.com.portosoftware.chamados.core.model.CallStatus.IN_PROGRESS
import br.com.portosoftware.chamados.core.model.CallStatus.OPENED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRulesTest {
    /** Cópia literal de `CallService.allowedTransitions` (backend). Se o backend mudar, este teste deve mudar junto. */
    private val backendTable =
        mapOf(
            OPENED to setOf(IN_PROGRESS, CLOSED),
            IN_PROGRESS to setOf(OPENED, CLOSED),
            CLOSED to emptySet(),
        )

    @Test
    fun `transicoes espelham a tabela do backend para todos os status`() {
        (CallStatus.entries - CallStatus.UNKNOWN).forEach { from ->
            assertEquals("de $from", backendTable.getValue(from), CallRules.allowedTransitions(from).toSet())
        }
    }

    @Test
    fun `opcoes do atendimento comecam pelo status atual`() {
        (CallStatus.entries - CallStatus.UNKNOWN).forEach { status ->
            assertEquals(status, CallRules.progressOptions(status).first())
        }
    }

    @Test
    fun `status desconhecido nao libera transicao, atendimento nem anexo`() {
        assertEquals(emptyList<CallStatus>(), CallRules.allowedTransitions(CallStatus.UNKNOWN))
        assertEquals(emptyList<CallStatus>(), CallRules.progressOptions(CallStatus.UNKNOWN))
        assertFalse(CallRules.acceptsAttachments(CallStatus.UNKNOWN))
    }

    @Test
    fun `valor desconhecido nao aparece para o usuario escolher`() {
        assertFalse(CallStatus.UNKNOWN in CallRules.filterableStatuses)
        assertFalse(CallStatus.UNKNOWN in CallRules.initialStatuses)
        assertFalse(CallPriority.UNKNOWN in CallPriority.selectable)
    }

    @Test
    fun `criacao aceita so aberto, em atendimento e fechado`() {
        assertEquals(listOf(OPENED, IN_PROGRESS, CLOSED), CallRules.initialStatuses)
    }

    @Test
    fun `chamado fechado nao recebe anexo`() {
        assertTrue(CallRules.acceptsAttachments(OPENED))
        assertTrue(CallRules.acceptsAttachments(IN_PROGRESS))
        assertFalse(CallRules.acceptsAttachments(CLOSED))
    }

    @Test
    fun `formato de anexo segue a lista do backend, sem diferenciar maiusculas`() {
        listOf("a.pdf", "a.png", "a.jpg", "a.JPEG", "a.txt", "a.doc", "a.docx", "a.xls", "a.xlsx", "a.b.PDF").forEach {
            assertTrue(it, CallRules.isAttachmentFormatAllowed(it))
        }
        listOf("video.mp4", "foto.heic", "foto.webp", "semextensao", "ponto.").forEach {
            assertFalse(it, CallRules.isAttachmentFormatAllowed(it))
        }
    }

    @Test
    fun `envio de anexos reserva espaco para o multipart dentro dos 25 MB`() {
        val mb = 1024L * 1024
        assertTrue(CallRules.fitsUploadRequest(emptyList()))
        assertTrue(CallRules.fitsUploadRequest(listOf(10 * mb, 10 * mb)))
        assertFalse(CallRules.fitsUploadRequest(listOf(10 * mb, 10 * mb, 5 * mb)))
        assertTrue(CallRules.fitsUploadRequest(listOf(10 * mb, 10 * mb, 5 * mb - 3 * 1024)))
    }

    @Test
    fun `item de tipo unico vai um por atendimento`() {
        val unique = StockItem("i", "Fonte", null, "Fonte", 3, false, null, null, emptyList(), isUnique = true)
        assertEquals(1, unique.maxPerCall)
        assertEquals(0, unique.copy(quantity = 0).maxPerCall)
        assertEquals(3, unique.copy(isUnique = false).maxPerCall)
    }

    @Test
    fun `pagina diz quantos itens nao vieram`() {
        assertEquals(3L, Page(listOf("a", "b"), 0, 3, 5).notLoaded)
        assertEquals(0L, Page(listOf("a", "b"), 0, 1, 2).notLoaded)
    }
}
