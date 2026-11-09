package br.com.portosoftware.chamados.data.remote

import br.com.portosoftware.chamados.contract.model.ApplicantRequestDTO
import br.com.portosoftware.chamados.contract.model.ApplicantResponseDTO
import br.com.portosoftware.chamados.contract.model.AuthRequest
import br.com.portosoftware.chamados.contract.model.CallAttachmentResponseDTO
import br.com.portosoftware.chamados.contract.model.CallDashboardResponseDTO
import br.com.portosoftware.chamados.contract.model.CallProgressRequestDTO
import br.com.portosoftware.chamados.contract.model.CallRequestDTO
import br.com.portosoftware.chamados.contract.model.CallResponseDTO
import br.com.portosoftware.chamados.contract.model.EquipmentResponseDTO
import br.com.portosoftware.chamados.contract.model.LocalResponseDTO
import br.com.portosoftware.chamados.contract.model.PagedModelCallSummaryResponseDTO
import br.com.portosoftware.chamados.contract.model.PagedModelCategoryResponseDTO
import br.com.portosoftware.chamados.contract.model.PagedModelItemResponseDTO
import br.com.portosoftware.chamados.contract.model.PositionRequestDTO
import br.com.portosoftware.chamados.contract.model.PositionResponseDTO
import br.com.portosoftware.chamados.contract.model.ResetPasswordRequestDTO
import br.com.portosoftware.chamados.contract.model.SessionInfoResponseDTO
import br.com.portosoftware.chamados.contract.model.SubCategoryResponseDTO
import br.com.portosoftware.chamados.contract.model.UnitResponseDTO
import br.com.portosoftware.chamados.contract.model.UserResponseDTO
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints da API Spring Boot usados pelo app (base: `/api/`). Corpos e respostas são os modelos gerados de
 * `contracts/openapi.yaml` (pacote `contract.model`); os mappers os convertem para o domínio.
 *
 * A renovação de sessão (`POST /auth/refresh`) não está aqui: o `TokenRefreshAuthenticator` a faz por fora do Retrofit.
 */
interface ChamadosApi {
    @GET("auth/csrf")
    suspend fun csrf(): Response<Unit>

    /** O app não lê a sessão devolvida, mas a resposta tipada faz um 200 que não seja a sessão falhar o login. */
    @POST("auth/login")
    suspend fun login(
        @Body body: AuthRequest,
    ): SessionInfoResponseDTO

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body body: ResetPasswordRequestDTO,
    ): Response<Unit>

    @GET("auth/me")
    suspend fun me(): UserResponseDTO

    @GET("dashboard/calls")
    suspend fun dashboard(): CallDashboardResponseDTO

    /** `statuses` e `priority` saem dos enums do contrato (`EnumMappers.kt`), nunca do nome do enum do domínio. */
    @GET("calls/filter")
    suspend fun filterCalls(
        @Query("search") search: String?,
        @Query("status") statuses: List<String>,
        @Query("priority") priority: String?,
        @Query("categoryId") categoryId: String?,
        @Query("responsibleTechnicianId") technicianId: String?,
        @Query("unitId") unitId: String?,
        @Query("unassigned") unassigned: Boolean?,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PagedModelCallSummaryResponseDTO

    @GET("calls/{id}")
    suspend fun call(
        @Path("id") id: String,
    ): CallResponseDTO

    @POST("calls")
    suspend fun createCall(
        @Body body: CallRequestDTO,
    ): CallResponseDTO

    @PATCH("calls/{id}/progress")
    suspend fun progress(
        @Path("id") id: String,
        @Body body: CallProgressRequestDTO,
    ): CallResponseDTO

    @Multipart
    @POST("calls/{id}/attachments")
    suspend fun uploadAttachments(
        @Path("id") id: String,
        @Part files: List<MultipartBody.Part>,
    ): List<CallAttachmentResponseDTO>

    @GET("unit/secretary/{id}")
    suspend fun units(
        @Path("id") secretaryId: String,
    ): List<UnitResponseDTO>

    @GET("local/unit/{id}")
    suspend fun locals(
        @Path("id") unitId: String,
    ): List<LocalResponseDTO>

    @GET("equipment/local/{id}")
    suspend fun equipment(
        @Path("id") localId: String,
    ): Response<List<EquipmentResponseDTO>>

    @GET("category/all")
    suspend fun categories(
        @Query("size") size: Int = 200,
    ): PagedModelCategoryResponseDTO

    /** Responde `[]` quando a categoria não tem subcategorias. */
    @GET("sub-category/category/{id}")
    suspend fun subcategories(
        @Path("id") categoryId: String,
    ): Response<List<SubCategoryResponseDTO>>

    @GET("users/technicians")
    suspend fun technicians(): List<UserResponseDTO>

    @GET("applicants/search")
    suspend fun searchApplicants(
        @Query("q") query: String,
    ): List<ApplicantResponseDTO>

    @POST("applicants")
    suspend fun createApplicant(
        @Body body: ApplicantRequestDTO,
    ): ApplicantResponseDTO

    @GET("positions/active")
    suspend fun positions(): List<PositionResponseDTO>

    @POST("positions")
    suspend fun createPosition(
        @Body body: PositionRequestDTO,
    ): PositionResponseDTO

    @GET("items/all")
    suspend fun stock(
        @Query("search") search: String?,
        @Query("status") status: String = "STOCK",
        @Query("size") size: Int = 200,
    ): PagedModelItemResponseDTO
}
