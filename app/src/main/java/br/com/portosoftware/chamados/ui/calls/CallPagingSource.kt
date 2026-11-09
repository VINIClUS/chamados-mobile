package br.com.portosoftware.chamados.ui.calls

import androidx.paging.PagingSource
import androidx.paging.PagingState
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.model.AppResult
import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.DomainException

/** Paginação de `GET /calls/filter` (page base 0). Funciona igual com fake ou API. */
class CallPagingSource(
    private val repository: CallRepository,
    private val filter: CallFilter,
) : PagingSource<Int, CallSummary>() {
    override fun getRefreshKey(state: PagingState<Int, CallSummary>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) }
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CallSummary> {
        val page = params.key ?: 0
        return when (val result = repository.search(filter, page, params.loadSize.coerceAtMost(PAGE_SIZE))) {
            is AppResult.Success -> {
                LoadResult.Page(
                    data = result.value.items,
                    prevKey = if (page == 0) null else page - 1,
                    nextKey = if (result.value.isLast || result.value.items.isEmpty()) null else page + 1,
                )
            }

            is AppResult.Failure -> {
                LoadResult.Error(DomainException(result.error))
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
