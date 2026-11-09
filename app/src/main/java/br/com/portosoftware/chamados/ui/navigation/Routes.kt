package br.com.portosoftware.chamados.ui.navigation

import kotlinx.serialization.Serializable

// Rotas type-safe (Navigation Compose + kotlinx.serialization).

@Serializable data object HomeRoute

/** [preset] vem dos atalhos de indicadores do Início (ver [CallListPreset]). */
@Serializable data class CallsRoute(
    val preset: String? = null,
)

@Serializable data object StockRoute

@Serializable data object ProfileRoute

@Serializable data class CallDetailRoute(
    val id: String,
)

@Serializable data class ProgressRoute(
    val callId: String,
)

@Serializable data object NewCallRoute

/** Atalhos que abrem Chamados já filtrado. */
enum class CallListPreset { ACTIVE, NEW, IN_PROGRESS, URGENT, UNASSIGNED }

/** Chave usada para Atender devolver o resultado ao Detalhe. */
const val PROGRESS_SAVED_KEY = "progress_saved"
