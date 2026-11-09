package br.com.portosoftware.chamados.ui.preview

import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallAttachment
import br.com.portosoftware.chamados.core.model.CallDashboard
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallHistoryEntry
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.Equipment
import br.com.portosoftware.chamados.core.model.Role
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.core.model.User
import java.time.LocalDateTime

/** Dados fixos para Compose Previews (independentes do flavor). */
object PreviewData {
    private val now: LocalDateTime = LocalDateTime.now()

    val secretary = Secretary("sec-saude", "Secretaria de Saúde")
    val user =
        User(
            "u-tec",
            "Carlos Técnico",
            "tecnico@chamados.test",
            Role.TECHNICAL,
            listOf(secretary, Secretary("sec-edu", "Secretaria de Educação")),
        )
    val technician = Technician("u-tec", "Carlos Técnico")
    val maria = Applicant("ap-1", "Maria Souza", "maria.souza@prefeitura.test", "(31) 3333-1234", "un-1", "UBS Centro", "lo-1", "Recepção")

    val calls =
        listOf(
            CallSummary(
                "c0a80001-aaaa",
                "Computador da recepção não liga após queda de energia. Paciente aguardando.",
                CallStatus.OPENED,
                CallPriority.URGENT,
                now.minusMinutes(40),
                "Maria Souza",
                "UBS Centro",
                "Recepção",
                "Hardware",
                "Não liga",
                null,
                "PAT-004512",
            ),
            CallSummary(
                "c0a80002-bbbb",
                "Sistema de farmácia mostra erro ao salvar dispensação.",
                CallStatus.IN_PROGRESS,
                CallPriority.HIGH,
                now.minusHours(3),
                "João Pereira",
                "UBS Centro",
                "Farmácia",
                "Sistemas",
                "Erro ao salvar",
                "Carlos Técnico",
                null,
            ),
            CallSummary(
                "c0a80003-cccc",
                "Impressora atolando papel em toda impressão.",
                CallStatus.IN_PROGRESS,
                CallPriority.NORMAL,
                now.minusHours(26),
                "Maria Souza",
                "UBS Centro",
                "Recepção",
                "Impressão",
                "Atolamento de papel",
                "Carlos Técnico",
                "PAT-004513",
            ),
            CallSummary(
                "c0a80004-dddd",
                "Troca de senha do e-mail institucional.",
                CallStatus.CLOSED,
                CallPriority.LOW,
                now.minusDays(2),
                "Joana Ribeiro",
                "Hospital Municipal",
                "Administração",
                "Acesso e senhas",
                "Troca de senha",
                "Bruno Lima",
                null,
            ),
        )

    val dashboard =
        CallDashboard(totalActive = 9, newCalls = 4, inProgress = 3, urgent = 2, unassigned = 4, recentCalls = calls.take(3))

    val detail =
        CallDetail(
            id = "c0a80002-bbbb",
            description = "Sistema de farmácia mostra erro ao salvar dispensação de medicamentos.",
            status = CallStatus.IN_PROGRESS,
            priority = CallPriority.HIGH,
            openedAt = now.minusHours(3),
            completedAt = null,
            applicant = maria,
            unitName = "UBS Centro",
            localName = "Farmácia",
            categoryName = "Sistemas",
            subcategoryName = "Erro ao salvar",
            equipment = Equipment("eq-3", "PAT-003311", "Lenovo ThinkCentre M70"),
            technician = technician,
            openedByName = "Ana Coordenadora",
            history =
                listOf(
                    CallHistoryEntry("h1", now.minusHours(3), "Chamado aberto por WhatsApp.", "Ana Coordenadora"),
                    CallHistoryEntry(
                        "h2",
                        now.minusHours(2),
                        "Reinstalado o cliente do sistema; aguardando teste da farmacêutica.",
                        "Carlos Técnico",
                    ),
                ),
            attachments = listOf(CallAttachment("at-1", "erro-farmacia.jpg", "image/jpeg", 482_000)),
        )

    val openUnassigned =
        detail.copy(
            id = "c0a80001-aaaa",
            status = CallStatus.OPENED,
            priority = CallPriority.URGENT,
            technician = null,
            attachments = emptyList(),
        )

    val stock =
        listOf(
            StockItem("it-1", "Memória DDR4 8 GB", "Kingston", "Memória RAM", 14, false, null, null, listOf("Capacidade" to "8 GB")),
            StockItem("it-3", "Toner TN-3442", "Brother", "Toner", 2, true, null, null, emptyList()),
            StockItem("it-4", "Mouse óptico USB", "Logitech", "Periférico", 25, true, null, null, emptyList()),
            StockItem("it-8", "Roteador Wi-Fi AC1200", "TP-Link", "Rede", 0, false, null, null, emptyList()),
        )
}
