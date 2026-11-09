package br.com.portosoftware.chamados.data.fake

import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallDetail
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.core.model.Position
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.core.model.StockItem
import br.com.portosoftware.chamados.core.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Estado em memória compartilhado pelos repositórios fake, como se fosse o servidor. */
@Singleton
class FakeBackend
    @Inject
    constructor() {
        private val mutex = Mutex()

        var currentUser: User? = null
        var activeSecretary: Secretary? = null

        val calls: MutableList<CallDetail> = FakeData.initialCalls(LocalDateTime.now())
        val applicants: MutableList<Applicant> = FakeData.applicants.toMutableList()
        val positions: MutableList<Position> = FakeData.positions.toMutableList()
        val stock: MutableList<StockItem> = FakeData.stock.toMutableList()
        private var sequence = 1000

        fun nextId(prefix: String): String = "$prefix-${sequence++}"

        /** Unidades da secretaria ativa, usadas para isolar dados como o header X-Active-Secretary-Id. */
        fun activeUnitNames(): Set<String> =
            FakeData.units[activeSecretary?.id]
                .orEmpty()
                .map { it.name }
                .toSet()

        suspend fun <T> call(block: suspend FakeBackend.() -> AppResult<T>): AppResult<T> {
            delay(FakeData.LATENCY_MS)
            return mutex.withLock { block() }
        }

        fun <T> ok(value: T): AppResult<T> = AppResult.Success(value)

        fun fail(error: DomainError): AppResult<Nothing> = AppResult.Failure(error)
    }
