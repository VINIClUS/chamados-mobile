package br.com.portosoftware.chamados.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePolicyTest {
    @Test
    fun `papeis sao os mesmos do backend`() {
        assertEquals(
            listOf("ADMIN", "TECHNICAL_ADMIN", "TECHNICAL", "VIEWER", "SYSTEM_EXTERNAL"),
            (Role.entries - Role.UNKNOWN).map { it.name },
        )
    }

    @Test
    fun `papel desconhecido nao libera nada e fica so com o perfil`() {
        val p = RolePolicy(Role.UNKNOWN)
        assertEquals(listOf(TopLevel.PROFILE), p.destinations)
        assertTrue(p.hasNoAppContent)
        assertFalse(p.canCreateCall)
        assertFalse(p.canAttend)
        assertFalse(p.canUploadAttachments)
        assertFalse(p.canListTechnicians)
    }

    @Test
    fun `equipe tecnica ve os quatro destinos e pode criar e atender`() {
        listOf(Role.ADMIN, Role.TECHNICAL_ADMIN, Role.TECHNICAL).forEach { role ->
            val p = RolePolicy(role)
            assertEquals(listOf(TopLevel.HOME, TopLevel.CALLS, TopLevel.STOCK, TopLevel.PROFILE), p.destinations)
            assertTrue(p.canCreateCall)
            assertTrue(p.canAttend)
            assertTrue(p.canUploadAttachments)
            assertTrue(p.showsNavigationBar)
            assertFalse(p.hasNoAppContent)
        }
    }

    @Test
    fun `sistema externo le tudo mas nao cria nem atende`() {
        val p = RolePolicy(Role.SYSTEM_EXTERNAL)
        assertEquals(listOf(TopLevel.HOME, TopLevel.CALLS, TopLevel.STOCK, TopLevel.PROFILE), p.destinations)
        assertFalse(p.canCreateCall)
        assertFalse(p.canAttend)
        assertFalse(p.canUploadAttachments)
        assertFalse(p.hasNoAppContent)
    }

    @Test
    fun `viewer fica so com o perfil, sem voltar para uma tela vazia`() {
        val p = RolePolicy(Role.VIEWER)
        assertEquals(listOf(TopLevel.PROFILE), p.destinations)
        assertEquals(TopLevel.PROFILE, p.start)
        assertFalse(p.showsNavigationBar)
        assertFalse(p.profileHasBack)
        assertTrue(p.hasNoAppContent)
        assertFalse(p.canCreateCall)
        assertFalse(p.canAttend)
    }

    @Test
    fun `so quem pode chamar users technicians ve o filtro de tecnico`() {
        val allowed = Role.entries.filter { RolePolicy(it).canListTechnicians }
        assertEquals(listOf(Role.ADMIN, Role.TECHNICAL_ADMIN, Role.TECHNICAL), allowed)
    }
}
