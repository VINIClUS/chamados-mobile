package br.com.portosoftware.chamados.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import br.com.portosoftware.chamados.core.data.CatalogRepository
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.data.StockRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Page
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.data.fake.FakeApplicantRepository
import br.com.portosoftware.chamados.ui.auth.LoginViewModel
import br.com.portosoftware.chamados.ui.auth.SecretaryPickerViewModel
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.detail.CallDetailViewModel
import br.com.portosoftware.chamados.ui.home.HomeViewModel
import br.com.portosoftware.chamados.ui.newcall.DraftAttachment
import br.com.portosoftware.chamados.ui.newcall.NewCallViewModel
import br.com.portosoftware.chamados.ui.progress.ProgressViewModel
import br.com.portosoftware.chamados.ui.stock.StockViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ViewModels contra os repositórios fake (mesmos contratos da API).
 * Roda em Robolectric porque as rotas type-safe são lidas do SavedStateHandle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewModelTest {
    private lateinit var env: FakeEnv

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        env = FakeEnv()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `login com credencial invalida mostra erro inline e nao abre sessao`() =
        runTest {
            env.auth.restore()
            val vm = LoginViewModel(env.auth)
            vm.onEmailChange("desconhecido@chamados.test")
            vm.onPasswordChange("errada")
            vm.submit()
            assertTrue(vm.state.value.loading)
            advanceUntilIdle()
            assertEquals("E-mail ou senha inválidos.", vm.state.value.error)
            assertEquals(SessionState.SignedOut, env.auth.session.value)
        }

    @Test
    fun `login sem conexao avisa no snackbar e nao marca a senha como errada`() =
        runTest {
            val vm = LoginViewModel(env.auth)
            vm.onEmailChange("tecnico@chamados.test")
            vm.onPasswordChange("offline")
            vm.submit()
            advanceUntilIdle()
            assertNull(vm.state.value.error)
            assertEquals("Sem conexão com o servidor.", vm.state.value.message)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun `ultima secretaria usada vale so para o proprio usuario`() =
        runTest {
            env.loginAs("admin@chamados.test")
            val education = (env.auth.session.value as SessionState.ChoosingSecretary).user.secretaries.last()
            env.auth.selectSecretary(education)
            env.auth.logout()

            val picker = SecretaryPickerViewModel(env.auth, env.settings)
            backgroundScope.launch { picker.state.collect {} }
            env.loginAs("leitor@chamados.test")
            advanceUntilIdle()
            assertNull(picker.state.value.lastUsedId)

            env.auth.logout()
            env.loginAs("admin@chamados.test")
            advanceUntilIdle()
            assertEquals(education.id, picker.state.value.lastUsedId)
        }

    @Test
    fun `login de quem tem uma secretaria vai direto para o app`() =
        runTest {
            val vm = LoginViewModel(env.auth)
            vm.onEmailChange("tecnico@chamados.test")
            vm.onPasswordChange("demo")
            vm.submit()
            advanceUntilIdle()
            assertTrue(env.auth.session.value is SessionState.Active)
        }

    @Test
    fun `login de quem tem varias secretarias pede a escolha`() =
        runTest {
            env.loginAs("admin@chamados.test")
            assertTrue(env.auth.session.value is SessionState.ChoosingSecretary)
        }

    /** Representa as telas da sessão: só registra se foi limpo. */
    class SessionProbe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun `trocar de secretaria ou sair limpa os ViewModels da sessao anterior`() =
        runTest {
            val app = AppViewModel(env.auth, env.settings)
            advanceUntilIdle()
            env.loginAs("admin@chamados.test")
            val secretaries = (env.auth.session.value as SessionState.ChoosingSecretary).user.secretaries
            env.auth.selectSecretary(secretaries.first())
            advanceUntilIdle()

            val firstOwner = app.sessionStoreOwner(env.auth.session.value as SessionState.Active)
            val first = ViewModelProvider(firstOwner, ViewModelProvider.NewInstanceFactory())[SessionProbe::class.java]
            // A mesma sessão devolve o mesmo dono (girar a tela não perde as telas).
            assertSame(firstOwner, app.sessionStoreOwner(env.auth.session.value as SessionState.Active))

            env.auth.selectSecretary(secretaries.last())
            advanceUntilIdle()
            assertTrue(first.cleared)
            val secondOwner = app.sessionStoreOwner(env.auth.session.value as SessionState.Active)
            assertNotSame(firstOwner, secondOwner)
            val second = ViewModelProvider(secondOwner, ViewModelProvider.NewInstanceFactory())[SessionProbe::class.java]

            env.auth.logout()
            advanceUntilIdle()
            assertTrue(second.cleared)
        }

    @Test
    fun `inicio carrega indicadores da secretaria ativa`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val vm = HomeViewModel(env.calls, env.auth)
            assertEquals(UiState.Loading, vm.state.value.dashboard)
            advanceUntilIdle()
            val data = (vm.state.value.dashboard as UiState.Success).data
            assertTrue(data.urgent >= 2)
            assertTrue(data.recentCalls.isNotEmpty())
            assertTrue(vm.state.value.canCreateCall)
        }

    @Test
    fun `assumir chamado aberto vira em atendimento com o tecnico e nota automatica`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val open = env.backend.calls.first { it.status == CallStatus.OPENED && it.technician == null && it.unitName == "UBS Centro" }
            val vm = CallDetailViewModel(SavedStateHandle(mapOf("id" to open.id)), env.calls, env.auth)
            advanceUntilIdle()
            assertTrue(vm.state.value.canAssume)

            vm.assume()
            advanceUntilIdle()

            val call = (vm.state.value.call as UiState.Success).data
            assertEquals(CallStatus.IN_PROGRESS, call.status)
            assertEquals("u-tec", call.technician?.id)
            assertTrue(
                call.history
                    .last()
                    .description
                    .startsWith("Atendimento iniciado por Carlos Técnico."),
            )
            assertFalse(vm.state.value.canAssume)
        }

    @Test
    fun `atender sem relato nao envia e marca o campo`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val call = env.backend.calls.first { it.status == CallStatus.IN_PROGRESS && it.unitName == "UBS Centro" }
            val vm = ProgressViewModel(SavedStateHandle(mapOf("callId" to call.id)), env.calls, env.catalog, env.stock, env.auth)
            advanceUntilIdle()
            assertEquals(CallStatus.IN_PROGRESS, vm.state.value.status)

            vm.submit()
            advanceUntilIdle()
            assertEquals("Descreva o que foi feito.", vm.state.value.noteError)
            assertFalse(vm.state.value.saved)
        }

    @Test
    fun `atender com peca resolve o chamado e baixa o estoque`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val call = env.backend.calls.first { it.status == CallStatus.IN_PROGRESS && it.unitName == "UBS Centro" }
            val vm = ProgressViewModel(SavedStateHandle(mapOf("callId" to call.id)), env.calls, env.catalog, env.stock, env.auth)
            advanceUntilIdle()
            assertTrue(CallStatus.CLOSED in vm.state.value.statusOptions)

            val memory = env.backend.stock.first { it.id == "it-1" }
            vm.onNoteChange("Troquei a memória.")
            vm.onStatus(CallStatus.CLOSED)
            vm.addPart(memory)
            vm.setQuantity("it-1", 2)
            vm.submit()
            advanceUntilIdle()

            assertTrue(vm.state.value.saved)
            assertEquals(
                CallStatus.CLOSED,
                env.backend.calls
                    .first { it.id == call.id }
                    .status,
            )
            assertEquals(
                memory.quantity - 2,
                env.backend.stock
                    .first { it.id == "it-1" }
                    .quantity,
            )
        }

    @Test
    fun `quantidade da peca fica limitada ao saldo`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val call = env.backend.calls.first { it.status == CallStatus.IN_PROGRESS && it.unitName == "UBS Centro" }
            val vm = ProgressViewModel(SavedStateHandle(mapOf("callId" to call.id)), env.calls, env.catalog, env.stock, env.auth)
            advanceUntilIdle()
            val toner = env.backend.stock.first { it.id == "it-3" } // saldo 2
            vm.addPart(toner)
            vm.setQuantity("it-3", 99)
            assertEquals(
                2,
                vm.state.value.parts
                    .single()
                    .quantity,
            )
            val noStock = env.backend.stock.first { it.quantity == 0 }
            vm.addPart(noStock)
            assertEquals(1, vm.state.value.parts.size)
        }

    @Test
    fun `chamado com status desconhecido nao oferece atender, assumir nem opcoes de atendimento`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val index =
                env.backend.calls.indexOfFirst { it.status == CallStatus.OPENED && it.isUnassigned && it.unitName == "UBS Centro" }
            val call = env.backend.calls[index]

            val known = CallDetailViewModel(SavedStateHandle(mapOf("id" to call.id)), env.calls, env.auth)
            advanceUntilIdle()
            assertTrue(known.state.value.showsAttend)
            assertTrue(known.state.value.canAssume)

            env.backend.calls[index] = call.copy(status = CallStatus.UNKNOWN)

            val detail = CallDetailViewModel(SavedStateHandle(mapOf("id" to call.id)), env.calls, env.auth)
            advanceUntilIdle()
            assertFalse(detail.state.value.showsAttend)
            assertFalse(detail.state.value.canAssume)

            val progress = ProgressViewModel(SavedStateHandle(mapOf("callId" to call.id)), env.calls, env.catalog, env.stock, env.auth)
            advanceUntilIdle()
            assertEquals(emptyList<CallStatus>(), progress.state.value.statusOptions)
            assertNull(progress.state.value.status)

            progress.onNoteChange("Tentativa de registrar atendimento.")
            progress.submit()
            advanceUntilIdle()
            assertEquals(CallStatus.UNKNOWN, env.backend.calls[index].status)
        }

    @Test
    fun `sem equipamento vinculado o atendimento nao oferece pecas`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val call = env.backend.calls.first { it.equipment == null && it.status == CallStatus.OPENED && it.unitName == "UBS Centro" }
            val vm = ProgressViewModel(SavedStateHandle(mapOf("callId" to call.id)), env.calls, env.catalog, env.stock, env.auth)
            advanceUntilIdle()
            assertFalse(vm.state.value.canUseParts)
        }

    @Test
    fun `chamado de outra secretaria responde nao encontrado, como a API`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val other = env.backend.calls.first { it.unitName == "E.M. Monteiro Lobato" }
            val result = env.calls.detail(other.id)
            // CallService.requireCall usa findByIdAndUnitSecretaryId: 404, sem revelar que o chamado existe.
            assertTrue((result as AppResult.Failure).error is DomainError.NotFound)
        }

    @Test
    fun `login aceito nao guarda a senha para depois do logout`() =
        runTest {
            val vm = LoginViewModel(env.auth)
            vm.onEmailChange("tecnico@chamados.test")
            vm.onPasswordChange("demo")
            vm.submit()
            advanceUntilIdle()
            env.auth.logout()
            advanceUntilIdle()

            assertEquals("", vm.state.value.password)
            assertFalse(vm.state.value.canSubmit)
        }

    @Test
    fun `atender chamado de outro tecnico mantem o responsavel atual`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val units = env.backend.activeUnitNames()
            val call = env.backend.calls.first { it.unitName in units && it.technician != null && it.technician.id != "u-tec" }
            val vm = ProgressViewModel(SavedStateHandle(mapOf("callId" to call.id)), env.calls, env.catalog, env.stock, env.auth)
            advanceUntilIdle()

            assertEquals(call.technician?.id, vm.state.value.technicianId)
        }

    @Test
    fun `trocar para solicitante de outra unidade nao deixa o local da unidade anterior`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val applicants = FakeApplicantRepository(env.backend)
            val vm = NewCallViewModel(SavedStateHandle(), env.calls, env.catalog, applicants, env.auth)
            advanceUntilIdle()
            vm.selectApplicant(env.backend.applicants.first { it.id == "ap-1" }) // UBS Centro, Recepção
            advanceUntilIdle()

            vm.selectApplicant(Applicant("ap-x", "Sem lotação", null, null, "un-hospital", "Hospital Municipal", null, null))
            advanceUntilIdle()

            val draft = vm.state.value.draft
            assertEquals("un-hospital", draft.unitId)
            assertNull(draft.localId)
            assertNull(vm.state.value.equipment)
            assertTrue(
                (vm.state.value.locals as UiState.Success).data.all { it.unitId == "un-hospital" },
            )
        }

    @Test
    fun `filtro de tipo que some da busca e desmarcado`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val vm = StockViewModel(env.stock, env.calls, env.auth)
            advanceUntilIdle()
            vm.onType("Toner")
            vm.onQueryChange("Mouse")
            advanceUntilIdle()

            assertNull(vm.state.value.type)
            assertTrue(
                vm.state.value.visible
                    .isNotEmpty(),
            )
        }

    @Test
    fun `estoque recarrega quando um atendimento baixa pecas`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val stockVm = StockViewModel(env.stock, env.calls, env.auth)
            advanceUntilIdle()
            val call = env.backend.calls.first { it.status == CallStatus.IN_PROGRESS && it.unitName == "UBS Centro" }
            val progress = ProgressViewModel(SavedStateHandle(mapOf("callId" to call.id)), env.calls, env.catalog, env.stock, env.auth)
            advanceUntilIdle()
            val memory = env.backend.stock.first { it.id == "it-1" }
            progress.onNoteChange("Troquei a memória.")
            progress.addPart(memory)
            progress.submit()
            advanceUntilIdle()

            val shown = (stockVm.state.value.items as UiState.Success).data.first { it.id == "it-1" }
            assertEquals(memory.quantity - 1, shown.quantity)
        }

    @Test
    fun `peca de tipo unico fica limitada a uma no atendimento`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val call = env.backend.calls.first { it.status == CallStatus.IN_PROGRESS && it.unitName == "UBS Centro" }
            val vm = ProgressViewModel(SavedStateHandle(mapOf("callId" to call.id)), env.calls, env.catalog, env.stock, env.auth)
            advanceUntilIdle()
            val power = env.backend.stock.first { it.id == "it-6" } // saldo 3, tipo único
            vm.addPart(power)
            vm.setQuantity("it-6", 3)
            assertEquals(
                1,
                vm.state.value.parts
                    .single()
                    .quantity,
            )
        }

    private fun newCallViewModel(catalog: CatalogRepository = env.catalog) =
        NewCallViewModel(SavedStateHandle(), env.calls, catalog, FakeApplicantRepository(env.backend), env.auth)

    @Test
    fun `escolher fechado retira os anexos, avisa e nao aceita outros`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val vm = newCallViewModel()
            advanceUntilIdle()
            vm.addAttachments(listOf(DraftAttachment("content://1", "foto.jpg", "image/jpeg", 1000)))
            assertEquals(
                1,
                vm.state.value.draft.attachments.size,
            )

            vm.onStatus(CallStatus.CLOSED)

            assertTrue(
                vm.state.value.draft.attachments
                    .isEmpty(),
            )
            assertNotNull(vm.state.value.errors["attachments"])
            vm.addAttachments(listOf(DraftAttachment("content://2", "outra.jpg", "image/jpeg", 1000)))
            assertTrue(
                vm.state.value.draft.attachments
                    .isEmpty(),
            )
        }

    @Test
    fun `anexo que o backend recusaria fica fora do rascunho, com o motivo`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val vm = newCallViewModel()
            advanceUntilIdle()
            val mb = 1024L * 1024

            vm.addAttachments(
                listOf(
                    DraftAttachment("content://1", "video.mp4", "video/mp4", 1000),
                    DraftAttachment("content://2", "grande.pdf", "application/pdf", 11 * mb),
                    DraftAttachment("content://3", "a.pdf", "application/pdf", 10 * mb),
                    DraftAttachment("content://3", "a.pdf", "application/pdf", 10 * mb),
                    DraftAttachment("content://4", "b.pdf", "application/pdf", 10 * mb),
                    DraftAttachment("content://5", "c.pdf", "application/pdf", 6 * mb),
                ),
            )

            assertEquals(
                listOf("a.pdf", "b.pdf"),
                vm.state.value.draft.attachments
                    .map { it.fileName },
            )
            val message =
                vm.state.value.errors
                    .getValue("attachments")
            listOf("video.mp4", "grande.pdf", "c.pdf").forEach { assertTrue(message, message.contains(it)) }
        }

    @Test
    fun `lista cortada na primeira pagina avisa quantos itens faltaram`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val first = env.backend.stock.first()
            val partial =
                object : StockRepository {
                    override suspend fun items(search: String) = AppResult.Success(Page<StockItem>(listOf(first), 0, 11, 205))
                }
            val catalog =
                object : CatalogRepository by env.catalog {
                    override suspend fun categories() = AppResult.Success(Page<Category>(emptyList(), 0, 3, 45))
                }

            val stockVm = StockViewModel(partial, env.calls, env.auth)
            val call = env.backend.calls.first { it.status == CallStatus.IN_PROGRESS && it.unitName == "UBS Centro" }
            val progress = ProgressViewModel(SavedStateHandle(mapOf("callId" to call.id)), env.calls, env.catalog, partial, env.auth)
            val newCall = newCallViewModel(catalog)
            advanceUntilIdle()
            progress.searchStock()
            advanceUntilIdle()

            assertEquals(204L, stockVm.state.value.notLoaded)
            assertEquals(204L, progress.state.value.stockNotLoaded)
            assertEquals(45L, newCall.state.value.categoriesNotLoaded)
        }

    @Test
    fun `comecar outro chamado mantem o aviso de categorias`() =
        runTest {
            env.loginAs("tecnico@chamados.test")
            val catalog =
                object : CatalogRepository by env.catalog {
                    override suspend fun categories() = AppResult.Success(Page<Category>(emptyList(), 0, 3, 45))
                }
            val vm = newCallViewModel(catalog)
            advanceUntilIdle()

            vm.startAnother()

            assertEquals(45L, vm.state.value.categoriesNotLoaded)
        }

    @Test
    fun `usuario sem equipe ve o aviso no lugar da lista de secretarias`() =
        runTest {
            val picker = SecretaryPickerViewModel(env.auth, env.settings)
            backgroundScope.launch { picker.state.collect {} }
            advanceUntilIdle()
            assertFalse(picker.state.value.hasNoSecretaries)

            env.loginAs("semequipe@chamados.test")
            advanceUntilIdle()

            assertTrue(picker.state.value.hasNoSecretaries)
        }
}
