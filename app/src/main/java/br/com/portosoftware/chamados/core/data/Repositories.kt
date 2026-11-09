package br.com.portosoftware.chamados.core.data

import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallAttachment
import br.com.portosoftware.chamados.core.model.CallDashboard
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.Equipment
import br.com.portosoftware.chamados.core.model.Local
import br.com.portosoftware.chamados.core.model.NewApplicant
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.Page
import br.com.portosoftware.chamados.core.model.PendingAttachment
import br.com.portosoftware.chamados.core.model.Position
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.SubCategory
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.core.model.ThemeMode
import br.com.portosoftware.chamados.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// Contratos que as telas usam. Cada flavor fornece uma implementação:
//   fake   → data/fake    (dados em memória, latência simulada)
//   remote → data/remote  (Retrofit + API Spring Boot)
// A escolha acontece só no RepositoryModule do flavor; nenhum ViewModel muda.

sealed interface SessionState {
    data object Restoring : SessionState

    data object SignedOut : SessionState

    data class ChoosingSecretary(
        val user: User,
    ) : SessionState

    data class Active(
        val user: User,
        val secretary: Secretary,
    ) : SessionState

    /** A renovação falhou no meio do uso: o app mostra "Sessão encerrada" e volta ao login. */
    data object Expired : SessionState
}

interface AuthRepository {
    val session: StateFlow<SessionState>

    /** Tenta reaproveitar a sessão salva (cookies) ao abrir o app. */
    suspend fun restore()

    suspend fun login(
        email: String,
        password: String,
    ): AppResult<kotlin.Unit>

    suspend fun selectSecretary(secretary: Secretary)

    suspend fun requestPasswordReset(email: String): AppResult<kotlin.Unit>

    suspend fun logout()

    /** Fecha o diálogo de sessão expirada e leva ao login. */
    fun acknowledgeExpired()
}

interface CallRepository {
    /** Emite sempre que um chamado é criado ou atualizado, para listas se recarregarem. */
    val changes: SharedFlow<String>

    suspend fun dashboard(): AppResult<CallDashboard>

    suspend fun search(
        filter: CallFilter,
        page: Int,
        size: Int,
    ): AppResult<Page<CallSummary>>

    suspend fun detail(id: String): AppResult<CallDetail>

    suspend fun registerProgress(
        id: String,
        request: ProgressRequest,
    ): AppResult<CallDetail>

    suspend fun create(call: NewCall): AppResult<CallDetail>

    suspend fun uploadAttachments(
        callId: String,
        files: List<PendingAttachment>,
    ): AppResult<List<CallAttachment>>
}

interface CatalogRepository {
    suspend fun units(secretaryId: String): AppResult<List<OrgUnit>>

    suspend fun locals(unitId: String): AppResult<List<Local>>

    suspend fun equipment(localId: String): AppResult<List<Equipment>>

    suspend fun categories(): AppResult<Page<Category>>

    suspend fun subcategories(categoryId: String): AppResult<List<SubCategory>>

    suspend fun technicians(): AppResult<List<Technician>>

    suspend fun positions(): AppResult<List<Position>>

    suspend fun createPosition(name: String): AppResult<Position>
}

interface ApplicantRepository {
    suspend fun search(query: String): AppResult<List<Applicant>>

    suspend fun create(applicant: NewApplicant): AppResult<Applicant>
}

interface StockRepository {
    /** Itens em estoque (`status=STOCK`) da secretaria ativa. */
    suspend fun items(search: String = ""): AppResult<Page<StockItem>>
}

/** Preferências locais, comuns aos dois flavors. */
interface SettingsRepository {
    val themeMode: Flow<ThemeMode>

    /** Última secretaria escolhida por [userId]. O aparelho pode ser compartilhado entre técnicos. */
    fun lastSecretaryId(userId: String): Flow<String?>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setLastSecretaryId(
        userId: String,
        secretaryId: String?,
    )
}

/** Sessão ativa atual (usuário + secretaria), ou null fora do app principal. */
fun AuthRepository.activeSession(): SessionState.Active? = session.value as? SessionState.Active
