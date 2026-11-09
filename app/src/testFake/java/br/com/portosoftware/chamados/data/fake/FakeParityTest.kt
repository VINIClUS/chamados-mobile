package br.com.portosoftware.chamados.data.fake

import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.NewApplicant
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.PendingAttachment
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.Role
import br.com.portosoftware.chamados.core.model.UsedStockItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** O fake precisa aceitar e recusar o mesmo que o CallService (src/main/java/.../service/CallService.java). */
class FakeParityTest {
    private lateinit var backend: FakeBackend
    private lateinit var calls: FakeCallRepository

    @Before
    fun setUp() {
        backend = FakeBackend()
        calls = FakeCallRepository(backend)
        backend.currentUser = FakeData.users.first { it.id == "u-tec" }
        backend.activeSecretary = FakeData.saude
    }

    private fun seed(n: Int): CallDetail {
        val id = "c0a8%04x-7f1e-4b2a-9c3d-%012d".format(Locale.ROOT, n, n)
        return backend.calls.first { it.id == id }
    }

    private fun stockOf(id: String) = backend.stock.first { it.id == id }.quantity

    private fun progress(
        status: CallStatus,
        technicianId: String? = "u-tec",
        parts: List<UsedStockItem> = emptyList(),
        note: String = "Atendimento.",
    ) = ProgressRequest(note, status, technicianId, parts)

    /** Chamado válido na Recepção da UBS Centro; cada teste muda só o que quer provar. */
    private val validCall =
        NewCall(
            description = "Monitor piscando desde a manhã.",
            status = CallStatus.OPENED,
            priority = CallPriority.NORMAL,
            applicantId = "ap-1",
            unitId = "un-ubs-centro",
            localId = "lo-recepcao",
            equipmentId = null,
            categoryId = "cat-hw",
            subcategoryId = "sub-nao-liga",
            technicianId = null,
        )

    private fun <T> AppResult<T>.error() = (this as AppResult.Failure).error

    private fun <T> AppResult<T>.value() = (this as AppResult.Success).value

    @Test
    fun `peca sem saldo no meio da lista nao baixa nenhuma peca nem muda o chamado`() =
        runTest {
            val call = seed(1) // tem equipamento
            val memoryBefore = stockOf("it-1")

            val result =
                calls.registerProgress(
                    call.id,
                    progress(CallStatus.IN_PROGRESS, parts = listOf(UsedStockItem("it-1", 2), UsedStockItem("it-8", 1))),
                )

            assertTrue(result.error() is DomainError.Validation)
            assertEquals(memoryBefore, stockOf("it-1"))
            assertEquals(call, seed(1))
        }

    @Test
    fun `quantidade nao positiva ou peca repetida sao recusadas`() =
        runTest {
            val call = seed(1)
            val before = stockOf("it-1")

            val negative = calls.registerProgress(call.id, progress(CallStatus.IN_PROGRESS, parts = listOf(UsedStockItem("it-1", -5))))
            val repeated =
                calls.registerProgress(
                    call.id,
                    progress(CallStatus.IN_PROGRESS, parts = listOf(UsedStockItem("it-1", 1), UsedStockItem("it-1", 1))),
                )

            assertTrue(negative.error() is DomainError.Validation)
            assertTrue(repeated.error() is DomainError.Validation)
            assertEquals(before, stockOf("it-1"))
        }

    @Test
    fun `chamado de outra secretaria nao pode ser lido nem alterado`() =
        runTest {
            val educationCall = seed(6) // E.M. Monteiro Lobato
            val before = stockOf("it-1")

            val detail = calls.detail(educationCall.id)
            val progress =
                calls.registerProgress(educationCall.id, progress(CallStatus.IN_PROGRESS, parts = listOf(UsedStockItem("it-1", 1))))

            assertTrue(detail.error() is DomainError.NotFound)
            assertTrue(progress.error() is DomainError.NotFound)
            assertEquals(educationCall, seed(6))
            assertEquals(before, stockOf("it-1"))
        }

    @Test
    fun `atendimento sem tecnico remove o responsavel e tecnico de outra secretaria e recusado`() =
        runTest {
            val call = seed(2) // Carlos
            backend.activeSecretary = FakeData.educacao
            val educationCall = seed(6)
            val foreign = calls.registerProgress(educationCall.id, progress(CallStatus.IN_PROGRESS, technicianId = "u-tec"))
            backend.activeSecretary = FakeData.saude

            val removed = calls.registerProgress(call.id, progress(CallStatus.IN_PROGRESS, technicianId = null)).value()

            assertTrue(foreign.error() is DomainError.Forbidden)
            assertNull(removed.technician)
            assertTrue(
                removed.history
                    .last()
                    .description
                    .contains("Técnico responsável removido."),
            )
        }

    @Test
    fun `data de conclusao so muda quando o status muda`() =
        runTest {
            val closed = seed(5)
            assertNotNull(closed.completedAt)

            val updated = calls.registerProgress(closed.id, progress(CallStatus.CLOSED, technicianId = "u-tec2")).value()

            assertEquals(closed.completedAt, updated.completedAt)
        }

    @Test
    fun `dashboard conta os status atuais e mostra os seis mais recentes`() =
        runTest {
            val visible = backend.calls.filter { it.unitName in backend.activeUnitNames() }

            val dashboard = calls.dashboard().value()

            assertEquals(visible.count { it.status == CallStatus.OPENED }.toLong(), dashboard.newCalls)
            assertEquals(visible.count { it.status == CallStatus.IN_PROGRESS }.toLong(), dashboard.inProgress)
            assertEquals(visible.sortedByDescending { it.openedAt }.take(6).map { it.id }, dashboard.recentCalls.map { it.id })
        }

    @Test
    fun `abrir como fechado nao pede relato e o historico e igual ao do backend`() =
        runTest {
            val created = calls.create(validCall.copy(status = CallStatus.CLOSED, priority = CallPriority.URGENT)).value()

            assertEquals(CallStatus.CLOSED, created.status)
            assertNotNull(created.completedAt)
            assertEquals(
                listOf("Chamado registrado com status Fechado e prioridade Urgente."),
                created.history.map { it.description },
            )
        }

    @Test
    fun `busca aceita varios status ao mesmo tempo`() =
        runTest {
            val wanted = setOf(CallStatus.OPENED, CallStatus.IN_PROGRESS)

            val found = calls.search(CallFilter(statuses = wanted), 0, 100).value().items
            val all = calls.search(CallFilter(), 0, 100).value().items

            assertEquals(wanted, found.map { it.status }.toSet())
            assertEquals(all.count { it.status in wanted }, found.size)
            assertTrue(all.any { it.status == CallStatus.CLOSED })
        }

    @Test
    fun `anexo em chamado fechado ou de formato nao aceito e recusado`() =
        runTest {
            val photo = PendingAttachment("content://1", "foto.jpg", "image/jpeg", 1000)
            val closed = calls.create(validCall.copy(status = CallStatus.CLOSED)).value()
            val open = calls.create(validCall).value()

            assertTrue(calls.uploadAttachments(closed.id, listOf(photo)).error() is DomainError.Validation)
            assertTrue(
                calls.uploadAttachments(open.id, listOf(photo.copy(fileName = "video.mp4"))).error() is DomainError.Validation,
            )
            assertEquals(1, calls.uploadAttachments(open.id, listOf(photo)).value().size)
        }

    @Test
    fun `envio de anexos que passa do limite total e recusado`() =
        runTest {
            val open = calls.create(validCall).value()
            val files = List(3) { PendingAttachment("content://$it", "foto$it.jpg", "image/jpeg", 9L * 1024 * 1024) }

            assertTrue(calls.uploadAttachments(open.id, files).error() is DomainError.Validation)
            assertEquals(2, calls.uploadAttachments(open.id, files.take(2)).value().size)
        }

    @Test
    fun `peca de tipo unico so entra uma por vez`() =
        runTest {
            val call = seed(1)
            val before = stockOf("it-6")

            val two = calls.registerProgress(call.id, progress(CallStatus.IN_PROGRESS, parts = listOf(UsedStockItem("it-6", 2))))

            assertTrue(two.error() is DomainError.Validation)
            assertEquals(before, stockOf("it-6"))
            calls.registerProgress(call.id, progress(CallStatus.IN_PROGRESS, parts = listOf(UsedStockItem("it-6", 1)))).value()
            assertEquals(before - 1, stockOf("it-6"))
        }

    @Test
    fun `cargo acima do limite do backend e recusado`() =
        runTest {
            val catalog = FakeCatalogRepository(backend)

            assertTrue(catalog.createPosition("x".repeat(151)).error() is DomainError.Validation)
            assertEquals("x".repeat(150), catalog.createPosition("x".repeat(150)).value().name)
        }

    @Test
    fun `criacao recusa combinacoes que o backend recusa`() =
        runTest {
            val wrongEquipment = calls.create(validCall.copy(equipmentId = "eq-5")) // eq-5 é do laboratório
            val wrongUnit = calls.create(validCall.copy(unitId = "un-hospital"))
            val wrongSymptom = calls.create(validCall.copy(categoryId = "cat-sw"))
            val otherSecretary = calls.create(validCall.copy(unitId = "un-escola-1", localId = "lo-lab"))
            val before = backend.calls.size

            assertTrue(wrongEquipment.error() is DomainError.Validation)
            assertTrue(wrongUnit.error() is DomainError.Validation)
            assertTrue(wrongSymptom.error() is DomainError.Validation)
            assertTrue(otherSecretary.error() is DomainError.Forbidden)
            assertEquals(before, backend.calls.size)
        }

    @Test
    fun `busca encontra por unidade e patrimonio, como o CallSpecification`() =
        runTest {
            val byUnit = calls.search(CallFilter(search = "hospital"), 0, 50).value()
            val byPatrimony = calls.search(CallFilter(search = seed(1).equipment!!.patrimony!!), 0, 50).value()

            assertTrue(byUnit.items.isNotEmpty() && byUnit.items.all { it.unitName == "Hospital Municipal" })
            assertTrue(byPatrimony.items.any { it.id == seed(1).id })
        }

    @Test
    fun `catalogos respeitam a secretaria ativa`() =
        runTest {
            backend.activeSecretary = FakeData.educacao
            val catalog = FakeCatalogRepository(backend)
            val applicants = FakeApplicantRepository(backend)

            val technicians = catalog.technicians().value().map { it.id }
            val maria = applicants.search("Maria").value().map { it.id }

            assertEquals(listOf("u-admin"), technicians)
            assertFalse("ap-1" in maria) // Maria é da UBS Centro (Saúde)
        }

    @Test
    fun `cadastro rapido valida telefone e local da secretaria`() =
        runTest {
            val applicants = FakeApplicantRepository(backend)

            val badPhone = applicants.create(NewApplicant("Lia", null, "31 9999", "lo-recepcao", "pos-enfermeiro"))
            val foreignLocal = applicants.create(NewApplicant("Lia", null, null, "lo-lab", "pos-enfermeiro"))
            val ok = applicants.create(NewApplicant("Lia", null, "(31)99999-0000", "lo-recepcao", "pos-enfermeiro"))

            assertTrue(badPhone.error() is DomainError.Validation)
            assertTrue(foreignLocal.error() is DomainError.Forbidden)
            assertEquals("un-ubs-centro", ok.value().unitId)
        }

    @Test
    fun `leitor recebe 403 ao listar tecnicos, como no SecurityConfig`() =
        runTest {
            backend.currentUser = FakeData.users.first { it.role == Role.VIEWER }

            assertTrue(FakeCatalogRepository(backend).technicians().error() is DomainError.Forbidden)
        }
}
