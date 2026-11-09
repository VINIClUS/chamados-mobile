package br.com.portosoftware.chamados.core.model

/**
 * Regras do backend (`CallService`) replicadas para a UI só oferecer o que é válido.
 * O backend continua sendo a autoridade; qualquer mudança lá precisa ser espelhada aqui.
 */
object CallRules {
    /** Status considerados "ativos" (fila de trabalho). */
    val activeStatuses = setOf(CallStatus.OPENED, CallStatus.IN_PROGRESS)

    /** Status que o usuário pode escolher ao filtrar a lista. */
    val filterableStatuses = listOf(CallStatus.OPENED, CallStatus.IN_PROGRESS, CallStatus.CLOSED)

    /** Espelho de `CallService.allowedTransitions`. */
    fun allowedTransitions(from: CallStatus): List<CallStatus> =
        when (from) {
            CallStatus.OPENED -> listOf(CallStatus.IN_PROGRESS, CallStatus.CLOSED)
            CallStatus.IN_PROGRESS -> listOf(CallStatus.OPENED, CallStatus.CLOSED)
            CallStatus.CLOSED, CallStatus.UNKNOWN -> emptyList()
        }

    /**
     * Opções do registro de atendimento: o status atual seguido das transições válidas.
     * Status desconhecido não oferece nenhuma, para nunca ser reenviado ao backend.
     */
    fun progressOptions(current: CallStatus): List<CallStatus> =
        if (current == CallStatus.UNKNOWN) emptyList() else listOf(current) + allowedTransitions(current)

    /** Status iniciais aceitos em `POST /calls`. */
    val initialStatuses = listOf(CallStatus.OPENED, CallStatus.IN_PROGRESS, CallStatus.CLOSED)

    const val NOTE_MAX = 1000
    const val DESCRIPTION_MIN = 10
    const val DESCRIPTION_MAX = 1000
    const val MAX_STOCK_LINES = 100
    const val POSITION_NAME_MAX = 150
    const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024

    /** `max-request-size` do backend e `client_max_body_size` do nginx: valem para o corpo inteiro do envio. */
    const val MAX_UPLOAD_REQUEST_BYTES = 25L * 1024 * 1024

    /** Reserva por arquivo para o que o multipart soma ao corpo: separador, nome e tipo. */
    private const val MULTIPART_OVERHEAD_BYTES = 1024L

    /** Espelho de `CallAttachmentService.ALLOWED_EXTENSIONS`. */
    private val attachmentExtensions = setOf("pdf", "png", "jpg", "jpeg", "txt", "doc", "docx", "xls", "xlsx")

    /** Mesma regra de `CallAttachmentService.extensionOf`: nome sem extensão é recusado. */
    fun isAttachmentFormatAllowed(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in attachmentExtensions

    fun fitsUploadRequest(sizes: List<Long>): Boolean = sizes.sum() + sizes.size * MULTIPART_OVERHEAD_BYTES <= MAX_UPLOAD_REQUEST_BYTES

    fun attachmentTooLargeMessage(fileName: String) = "$fileName passa de 10 MB."

    const val UPLOAD_TOO_LARGE_MESSAGE = "Os anexos passam do limite de 25 MB por envio."

    /** Chamado fechado não recebe anexo (`CallAttachmentService.upload`), nem logo depois de criado. */
    fun acceptsAttachments(status: CallStatus): Boolean = status == CallStatus.OPENED || status == CallStatus.IN_PROGRESS

    fun assumeNote(technicianName: String) = "Atendimento iniciado por $technicianName."
}
