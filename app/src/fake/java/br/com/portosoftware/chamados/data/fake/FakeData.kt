package br.com.portosoftware.chamados.data.fake

import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallAttachment
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallHistoryEntry
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.Equipment
import br.com.portosoftware.chamados.core.model.Local
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.Position
import br.com.portosoftware.chamados.core.model.Role
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.SubCategory
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.core.model.User
import java.time.LocalDateTime
import java.util.Locale

/**
 * Massa de dados do protótipo. No flavor fake, qualquer valor não vazio entra nas contas sintéticas:
 * admin@chamados.test, tecnico@chamados.test, leitor@chamados.test,
 * estoque@chamados.test e usuario@chamados.test.
 *
 * Para ver estados de erro: entre com a senha "offline", ou busque "erro"
 * em Chamados ou Estoque.
 */
object FakeData {
    const val LATENCY_MS = 600L
    const val ERROR_TRIGGER = "erro"

    val saude = Secretary("sec-saude", "Secretaria de Saúde")
    val educacao = Secretary("sec-educacao", "Secretaria de Educação")

    val users =
        listOf(
            User("u-admin", "Ana Coordenadora", "admin@chamados.test", Role.ADMIN, listOf(saude, educacao)),
            User("u-tec", "Carlos Técnico", "tecnico@chamados.test", Role.TECHNICAL, listOf(saude)),
            User("u-coord", "Paula Coordenação", "coordenacao@chamados.test", Role.TECHNICAL_ADMIN, listOf(saude)),
            User("u-viewer", "Vera Auditoria", "leitor@chamados.test", Role.VIEWER, listOf(saude, educacao)),
            User("u-externo", "Sistema Integrador", "externo@chamados.test", Role.SYSTEM_EXTERNAL, listOf(saude)),
            User("u-novato", "Nádia Sem Equipe", "semequipe@chamados.test", Role.TECHNICAL, emptyList()),
        )

    val technicians =
        listOf(
            Technician("u-tec", "Carlos Técnico"),
            Technician("u-admin", "Ana Coordenadora"),
            Technician("u-tec2", "Bruno Lima"),
        )

    val positions =
        listOf(
            Position("pos-medico", "Médico(a)"),
            Position("pos-enfermeiro", "Enfermeiro(a)"),
            Position("pos-tecnico-enfermagem", "Técnico(a) de Enfermagem"),
            Position("pos-escriturario", "Escriturário(a)"),
        )

    /** Secretarias de cada técnico; `GET /users/technicians` lista só os da secretaria ativa. */
    val technicianSecretaries =
        mapOf(
            "u-tec" to setOf(saude.id),
            "u-admin" to setOf(saude.id, educacao.id),
            "u-tec2" to setOf(saude.id),
        )

    val units =
        mapOf(
            saude.id to listOf(OrgUnit("un-ubs-centro", "UBS Centro"), OrgUnit("un-hospital", "Hospital Municipal")),
            educacao.id to listOf(OrgUnit("un-escola-1", "E.M. Monteiro Lobato"), OrgUnit("un-semed", "Sede SEMED")),
        )

    val locals =
        listOf(
            Local("lo-recepcao", "Recepção", "un-ubs-centro"),
            Local("lo-farmacia", "Farmácia", "un-ubs-centro"),
            Local("lo-consultorio", "Consultório 2", "un-ubs-centro"),
            Local("lo-pa", "Pronto Atendimento", "un-hospital"),
            Local("lo-adm", "Administração", "un-hospital"),
            Local("lo-secretaria", "Secretaria escolar", "un-escola-1"),
            Local("lo-lab", "Laboratório de informática", "un-escola-1"),
            Local("lo-gabinete", "Gabinete", "un-semed"),
        )

    val equipment =
        mapOf(
            "lo-recepcao" to
                listOf(
                    Equipment("eq-1", "PAT-004512", "Dell OptiPlex 3080"),
                    Equipment("eq-2", "PAT-004513", "Impressora Brother HL-L5102"),
                ),
            "lo-farmacia" to listOf(Equipment("eq-3", "PAT-003311", "Lenovo ThinkCentre M70")),
            "lo-pa" to listOf(Equipment("eq-4", "PAT-007001", "HP ProDesk 400")),
            "lo-lab" to listOf(Equipment("eq-5", "PAT-009120", "Positivo Master D610")),
        )

    val categories =
        listOf(
            Category("cat-hw", "Hardware"),
            Category("cat-sw", "Sistemas"),
            Category("cat-rede", "Rede e internet"),
            Category("cat-imp", "Impressão"),
            Category("cat-acesso", "Acesso e senhas"),
        )

    val subcategories =
        listOf(
            SubCategory("sub-nao-liga", "Não liga", "cat-hw"),
            SubCategory("sub-lento", "Lentidão", "cat-hw"),
            SubCategory("sub-periferico", "Mouse ou teclado", "cat-hw"),
            SubCategory("sub-erro-salvar", "Erro ao salvar", "cat-sw"),
            SubCategory("sub-instalacao", "Instalação", "cat-sw"),
            SubCategory("sub-sem-internet", "Sem internet", "cat-rede"),
            SubCategory("sub-wifi", "Wi-Fi instável", "cat-rede"),
            SubCategory("sub-papel", "Atolamento de papel", "cat-imp"),
            SubCategory("sub-toner", "Toner", "cat-imp"),
            SubCategory("sub-senha", "Troca de senha", "cat-acesso"),
        )

    val applicants =
        listOf(
            Applicant(
                "ap-1",
                "Maria Souza",
                "maria.souza@prefeitura.test",
                "(31)3333-1234",
                "un-ubs-centro",
                "UBS Centro",
                "lo-recepcao",
                "Recepção",
            ),
            Applicant(
                "ap-2",
                "João Pereira",
                "joao.pereira@prefeitura.test",
                "(31)98888-1200",
                "un-ubs-centro",
                "UBS Centro",
                "lo-farmacia",
                "Farmácia",
            ),
            Applicant("ap-3", "Marcos Alves", null, "(31)97777-4455", "un-hospital", "Hospital Municipal", "lo-pa", "Pronto Atendimento"),
            Applicant(
                "ap-4",
                "Mariana Costa",
                "mariana.costa@prefeitura.test",
                null,
                "un-escola-1",
                "E.M. Monteiro Lobato",
                "lo-lab",
                "Laboratório de informática",
            ),
            Applicant(
                "ap-5",
                "Joana Ribeiro",
                "joana.ribeiro@prefeitura.test",
                "(31)99999-0001",
                "un-hospital",
                "Hospital Municipal",
                "lo-adm",
                "Administração",
            ),
        )

    val stock =
        listOf(
            StockItem(
                "it-1",
                "Memória DDR4 8 GB",
                "Kingston",
                "Memória RAM",
                14,
                false,
                null,
                null,
                listOf(
                    "Capacidade" to "8 GB",
                    "Frequência" to "2666 MHz",
                ),
            ),
            StockItem("it-2", "SSD 240 GB", "Kingston", "Armazenamento", 6, false, null, null, listOf("Interface" to "SATA III")),
            StockItem("it-3", "Toner TN-3442", "Brother", "Toner", 2, true, null, null, listOf("Rendimento" to "3.000 páginas")),
            StockItem("it-4", "Mouse óptico USB", "Logitech", "Periférico", 25, true, null, null, emptyList()),
            StockItem("it-5", "Teclado ABNT2 USB", "Multilaser", "Periférico", 1, true, null, null, emptyList()),
            StockItem(
                "it-6",
                "Fonte ATX 500 W",
                "Corsair",
                "Fonte",
                3,
                false,
                "PAT-110022",
                "CX500-2291",
                listOf("Potência" to "500 W"),
                isUnique = true,
            ),
            StockItem("it-7", "Cabo de rede Cat6 (2 m)", "Furukawa", "Cabo", 40, true, null, null, emptyList()),
            StockItem("it-8", "Roteador Wi-Fi AC1200", "TP-Link", "Rede", 0, false, null, null, listOf("Padrão" to "802.11ac")),
        )

    fun initialCalls(now: LocalDateTime): MutableList<CallDetail> {
        val maria = applicants[0]
        val joao = applicants[1]
        val marcos = applicants[2]
        val mariana = applicants[3]
        val joana = applicants[4]
        val carlos = technicians[0]
        val bruno = technicians[2]

        fun call(
            n: Int,
            desc: String,
            status: CallStatus,
            priority: CallPriority,
            hoursAgo: Long,
            applicant: Applicant,
            category: String,
            sub: String?,
            tech: Technician?,
            equipment: Equipment? = null,
            history: List<CallHistoryEntry> = emptyList(),
            attachments: List<CallAttachment> = emptyList(),
        ) = CallDetail(
            id = "c0a8%04x-7f1e-4b2a-9c3d-%012d".format(Locale.ROOT, n, n),
            description = desc,
            status = status,
            priority = priority,
            openedAt = now.minusHours(hoursAgo),
            completedAt = if (status == CallStatus.CLOSED) now.minusHours(hoursAgo / 2) else null,
            applicant = applicant,
            unitName = applicant.unitName,
            localName = applicant.localName,
            categoryName = categories.first { it.id == category }.name,
            subcategoryName = sub?.let { s -> subcategories.first { it.id == s }.name },
            equipment = equipment,
            technician = tech,
            openedByName = "Ana Coordenadora",
            history =
                listOf(CallHistoryEntry("h-$n-0", now.minusHours(hoursAgo), "Chamado registrado.", "Ana Coordenadora")) + history,
            attachments = attachments,
        )
        return mutableListOf(
            call(
                1,
                "Computador da recepção não liga após queda de energia. Paciente aguardando agendamento.",
                CallStatus.OPENED,
                CallPriority.URGENT,
                1,
                maria,
                "cat-hw",
                "sub-nao-liga",
                null,
                equipment = equipment.getValue("lo-recepcao")[0],
            ),
            call(
                2,
                "Sistema de farmácia mostra erro ao salvar dispensação de medicamentos.",
                CallStatus.IN_PROGRESS,
                CallPriority.HIGH,
                3,
                joao,
                "cat-sw",
                "sub-erro-salvar",
                carlos,
                equipment = equipment.getValue("lo-farmacia")[0],
                history =
                    listOf(
                        CallHistoryEntry(
                            "h-2-1",
                            now.minusHours(2),
                            "Reinstalado o cliente do sistema; aguardando teste da farmacêutica.",
                            "Carlos Técnico",
                        ),
                    ),
                attachments = listOf(CallAttachment("at-1", "erro-farmacia.jpg", "image/jpeg", 482_000)),
            ),
            call(
                3,
                "Impressora atolando papel em toda impressão da recepção.",
                CallStatus.IN_PROGRESS,
                CallPriority.NORMAL,
                26,
                maria,
                "cat-imp",
                "sub-papel",
                carlos,
                equipment = equipment.getValue("lo-recepcao")[1],
                history =
                    listOf(
                        CallHistoryEntry("h-3-1", now.minusHours(20), "Aguardando peça (rolete) do fornecedor.", "Carlos Técnico"),
                    ),
            ),
            call(
                4,
                "Sem internet no Pronto Atendimento desde a manhã.",
                CallStatus.OPENED,
                CallPriority.URGENT,
                2,
                marcos,
                "cat-rede",
                "sub-sem-internet",
                null,
            ),
            call(
                5,
                "Troca de senha do e-mail institucional.",
                CallStatus.CLOSED,
                CallPriority.LOW,
                50,
                joana,
                "cat-acesso",
                "sub-senha",
                bruno,
                history = listOf(CallHistoryEntry("h-5-1", now.minusHours(30), "Senha redefinida e repassada por telefone.", "Bruno Lima")),
            ),
            call(
                6,
                "Laboratório: 5 computadores lentos para abrir o navegador.",
                CallStatus.OPENED,
                CallPriority.NORMAL,
                8,
                mariana,
                "cat-hw",
                "sub-lento",
                null,
                equipment = equipment.getValue("lo-lab")[0],
            ),
            call(
                7,
                "Wi-Fi cai a cada 10 minutos na administração.",
                CallStatus.IN_PROGRESS,
                CallPriority.HIGH,
                5,
                joana,
                "cat-rede",
                "sub-wifi",
                bruno,
            ),
            call(
                8,
                "Instalar leitor de PDF no consultório 2.",
                CallStatus.CLOSED,
                CallPriority.LOW,
                72,
                maria,
                "cat-sw",
                "sub-instalacao",
                carlos,
            ),
            call(
                9,
                "Toner acabando na impressora da farmácia.",
                CallStatus.OPENED,
                CallPriority.NORMAL,
                12,
                joao,
                "cat-imp",
                "sub-toner",
                null,
            ),
            call(
                10,
                "Teclado com teclas falhando na recepção.",
                CallStatus.CLOSED,
                CallPriority.LOW,
                96,
                maria,
                "cat-hw",
                "sub-periferico",
                null,
            ),
            call(
                11,
                "Manutenção preventiva do computador do Pronto Atendimento.",
                CallStatus.OPENED,
                CallPriority.NORMAL,
                120,
                marcos,
                "cat-sw",
                "sub-instalacao",
                null,
            ),
        ).also { list ->
            // Volume extra para exercitar a paginação.
            for (n in 12..45) {
                val app = applicants[n % applicants.size]
                list +=
                    call(
                        n,
                        "Manutenção preventiva agendada #$n.",
                        if (n % 3 == 0) CallStatus.CLOSED else CallStatus.OPENED,
                        CallPriority.LOW,
                        24L * (n - 8),
                        app,
                        "cat-hw",
                        "sub-lento",
                        if (n % 2 == 0) bruno else null,
                    )
            }
        }
    }
}
