package br.com.portosoftware.chamados.data.remote

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import br.com.portosoftware.chamados.contract.model.ApplicantRequestDTO
import br.com.portosoftware.chamados.contract.model.PositionRequestDTO
import br.com.portosoftware.chamados.core.data.ApplicantRepository
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.data.CatalogRepository
import br.com.portosoftware.chamados.core.data.IoDispatcher
import br.com.portosoftware.chamados.core.data.StockRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallAttachment
import br.com.portosoftware.chamados.core.model.CallDashboard
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.Category
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.DomainException
import br.com.portosoftware.chamados.core.model.Equipment
import br.com.portosoftware.chamados.core.model.Local
import br.com.portosoftware.chamados.core.model.NewApplicant
import br.com.portosoftware.chamados.core.model.NewCall
import br.com.portosoftware.chamados.core.model.OrgUnit
import br.com.portosoftware.chamados.core.model.Page
import br.com.portosoftware.chamados.core.model.PendingAttachment
import br.com.portosoftware.chamados.core.model.Position
import br.com.portosoftware.chamados.core.model.ProgressRequest
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.SubCategory
import br.com.portosoftware.chamados.core.model.Technician
import br.com.portosoftware.chamados.core.model.map
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.source
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteCallRepository
    @Inject
    constructor(
        private val api: ChamadosApi,
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : CallRepository {
        private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 8)
        override val changes: SharedFlow<String> = _changes.asSharedFlow()

        override suspend fun dashboard(): AppResult<CallDashboard> = apiCall { api.dashboard().toDomain() }

        override suspend fun search(
            filter: CallFilter,
            page: Int,
            size: Int,
        ): AppResult<Page<CallSummary>> =
            apiCall {
                api
                    .filterCalls(
                        search = filter.search.trim().ifBlank { null },
                        statuses = filter.statuses.map { it.toApi().value },
                        priority = filter.priority?.toApi()?.value,
                        categoryId = filter.categoryId,
                        technicianId = filter.technicianId,
                        unitId = filter.unitId,
                        unassigned = filter.unassigned.takeIf { it },
                        page = page,
                        size = size,
            ).let { response ->
                response.page?.toDomain(response.content.orEmpty().map { call -> call.toDomain() })
                    ?: error("Resposta paginada sem page")
            }
            }

        override suspend fun detail(id: String): AppResult<CallDetail> = apiCall { api.call(id).toDomain() }

        override suspend fun registerProgress(
            id: String,
            request: ProgressRequest,
        ): AppResult<CallDetail> =
            apiCall { api.progress(id, request.toDto()).toDomain() }.also { if (it is AppResult.Success) _changes.tryEmit(id) }

        override suspend fun create(call: NewCall): AppResult<CallDetail> =
            apiCall { api.createCall(call.toDto()).toDomain() }.also { if (it is AppResult.Success) _changes.tryEmit(it.value.id) }

        override suspend fun uploadAttachments(
            callId: String,
            files: List<PendingAttachment>,
        ): AppResult<List<CallAttachment>> =
            apiCall {
                val parts =
                    withContext(io) {
                        files.zip(readWithinLimits(files)) { file, bytes ->
                            MultipartBody.Part.createFormData(
                                "files",
                                file.fileName,
                                bytes.toRequestBody(file.mimeType?.toMediaTypeOrNull()),
                            )
                        }
                    }
                api
                    .uploadAttachments(callId, parts)
                    .map { it.toDomain() }
            }.also { if (it is AppResult.Success) _changes.tryEmit(callId) }

        /**
         * Lê os anexos e recusa, antes de abrir a conexão, o que o backend recusaria pelo tamanho, com o texto da escolha.
         * Conta os bytes lidos, e não o tamanho declarado: o provedor pode não informá-lo, e a escolha fica com 0. Lê no
         * máximo um byte além do limite de cada arquivo, e para quando o envio deixa de caber.
         */
        private fun readWithinLimits(files: List<PendingAttachment>): List<ByteArray> {
            val contents = mutableListOf<ByteArray>()
            for (file in files) {
                val bytes = read(file, atMost = CallRules.MAX_ATTACHMENT_BYTES + 1)
                val fits = CallRules.fitsUploadRequest((contents + bytes).map { it.size.toLong() })
                val refusal =
                    when {
                        bytes.size > CallRules.MAX_ATTACHMENT_BYTES -> CallRules.attachmentTooLargeMessage(file.fileName)
                        !fits -> CallRules.UPLOAD_TOO_LARGE_MESSAGE
                        else -> null
                    }
                if (refusal != null) throw DomainException(DomainError.Validation(refusal))
                contents += bytes
            }
            return contents
        }

        private fun read(
            file: PendingAttachment,
            atMost: Long,
        ): ByteArray {
            val stream =
                context.contentResolver.openInputStream(file.uri.toUri())
                    ?: throw FileNotFoundException("Sem acesso ao anexo ${file.fileName}")
            return stream.use { it.readAtMost(atMost) }
        }
    }

private fun InputStream.readAtMost(byteCount: Long): ByteArray {
    val buffer = Buffer()
    source().use { source ->
        while (buffer.size < byteCount && source.read(buffer, byteCount - buffer.size) != -1L) Unit
    }
    return buffer.readByteArray()
}

@Singleton
class RemoteCatalogRepository
    @Inject
    constructor(
        private val api: ChamadosApi,
    ) : CatalogRepository {
        override suspend fun units(secretaryId: String): AppResult<List<OrgUnit>> = apiCall { api.units(secretaryId).map { it.toDomain() } }

        override suspend fun locals(unitId: String): AppResult<List<Local>> = apiCall { api.locals(unitId).map { it.toDomain() } }

        override suspend fun equipment(localId: String): AppResult<List<Equipment>> =
            apiCall {
                api
                    .equipment(localId)
                    .bodyOrThrow()
                    .map { it.toDomain() }
            }

        override suspend fun categories(): AppResult<Page<Category>> =
        apiCall {
            api.categories().let { response ->
                response.page?.toDomain(response.content.orEmpty().map { category -> category.toDomain() })
                    ?: error("Resposta paginada sem page")
            }
        }

        override suspend fun subcategories(categoryId: String): AppResult<List<SubCategory>> =
            apiCall {
                api
                    .subcategories(categoryId)
                    .bodyOrThrow()
                    .map { it.toDomain() }
            }

        override suspend fun technicians(): AppResult<List<Technician>> = apiCall { api.technicians().map { it.toTechnician() } }

        override suspend fun positions(): AppResult<List<Position>> = apiCall { api.positions().map { it.toDomain() } }

        override suspend fun createPosition(name: String): AppResult<Position> =
            apiCall { api.createPosition(PositionRequestDTO(name.trim())).toDomain() }
    }

@Singleton
class RemoteApplicantRepository
    @Inject
    constructor(
        private val api: ChamadosApi,
    ) : ApplicantRepository {
        override suspend fun search(query: String): AppResult<List<Applicant>> =
            apiCall { api.searchApplicants(query.trim()).map { it.toDomain() } }

        override suspend fun create(applicant: NewApplicant): AppResult<Applicant> =
            apiCall {
                api
                    .createApplicant(
                        ApplicantRequestDTO(
                            name = applicant.name.trim(),
                            email = applicant.email?.trim()?.ifBlank { null },
                            phone = applicant.phone?.trim()?.ifBlank { null },
                            localId = applicant.localId,
                            positionId = applicant.positionId,
                        ),
                    ).toDomain()
            }
    }

@Singleton
class RemoteStockRepository
    @Inject
    constructor(
        private val api: ChamadosApi,
    ) : StockRepository {
        override suspend fun items(search: String): AppResult<Page<StockItem>> =
    apiCall {
        api.stock(search.trim().ifBlank { null }).let { response ->
            response.page?.toDomain(response.content.orEmpty().map { item -> item.toDomain() })
                ?: error("Resposta paginada sem page")
        }
    }
    }
