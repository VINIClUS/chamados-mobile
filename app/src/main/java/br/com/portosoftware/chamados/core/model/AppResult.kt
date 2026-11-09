package br.com.portosoftware.chamados.core.model

/** Erros que as telas sabem apresentar, independentes de HTTP. */
sealed interface DomainError {
    val message: String

    data class Network(
        override val message: String = "Sem conexão com o servidor.",
    ) : DomainError

    data class Unauthorized(
        override val message: String = "Sua sessão terminou. Entre novamente.",
    ) : DomainError

    data class Forbidden(
        override val message: String = "Você não tem acesso a este conteúdo.",
    ) : DomainError

    data class NotFound(
        override val message: String = "Não encontramos este registro.",
    ) : DomainError

    data class Validation(
        override val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : DomainError

    data class Unknown(
        override val message: String = "Algo deu errado. Tente novamente.",
    ) : DomainError
}

class DomainException(
    val error: DomainError,
) : Exception(error.message)

sealed interface AppResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AppResult<T>

    data class Failure(
        val error: DomainError,
    ) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

fun <T> AppResult<T>.getOrThrow(): T =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> throw DomainException(error)
    }
