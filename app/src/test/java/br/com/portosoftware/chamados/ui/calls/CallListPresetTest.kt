package br.com.portosoftware.chamados.ui.calls

import br.com.portosoftware.chamados.core.model.CallFilter
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CallListPresetTest {
    private val active = setOf(CallStatus.OPENED, CallStatus.IN_PROGRESS)

    @Test
    fun `atalhos do inicio mostram o mesmo recorte do card`() {
        assertEquals(active, CallRules.activeStatuses)
        assertEquals(CallFilter(statuses = active), CallListViewModel.presetFilter("ACTIVE"))
        assertEquals(CallFilter(statuses = setOf(CallStatus.OPENED)), CallListViewModel.presetFilter("NEW"))
        assertEquals(CallFilter(statuses = setOf(CallStatus.IN_PROGRESS)), CallListViewModel.presetFilter("IN_PROGRESS"))
        assertEquals(CallFilter(statuses = active, priority = CallPriority.URGENT), CallListViewModel.presetFilter("URGENT"))
        assertEquals(CallFilter(statuses = active, unassigned = true), CallListViewModel.presetFilter("UNASSIGNED"))
    }

    @Test
    fun `sem atalho a lista nao filtra`() {
        assertEquals(CallFilter(), CallListViewModel.presetFilter(null))
        assertEquals(CallFilter(), CallListViewModel.presetFilter("desconhecido"))
    }
}
