package br.com.portosoftware.chamados.ui.common

import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.DomainError

/** Espera entre a última tecla e a busca, como definido no doc de UX. */
const val SEARCH_DEBOUNCE_MS = 400L

/** Os quatro estados que toda tela de dados desenha (matriz de estados, §7 do doc de UX). */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data object Empty : UiState<Nothing>

    data class Error(
        val error: DomainError,
    ) : UiState<Nothing>

    data class Success<T>(
        val data: T,
    ) : UiState<T>
}

fun <T> AppResult<T>.toUiState(isEmpty: (T) -> Boolean = { false }): UiState<T> =
    when (this) {
        is AppResult.Success -> if (isEmpty(value)) UiState.Empty else UiState.Success(value)
        is AppResult.Failure -> UiState.Error(error)
    }

val <T> UiState<T>.dataOrNull: T? get() = (this as? UiState.Success)?.data
