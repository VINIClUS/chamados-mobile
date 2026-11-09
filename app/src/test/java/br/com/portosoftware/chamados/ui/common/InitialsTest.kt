package br.com.portosoftware.chamados.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test
    fun `usa o primeiro e o ultimo nome, sem a preposicao do meio`() {
        assertEquals("TT", initialsOf("Técnico de Teste"))
        assertEquals("MS", initialsOf("Maria da Silva Souza"))
    }

    @Test
    fun `nome unico vira uma letra e espacos extras nao contam`() {
        assertEquals("A", initialsOf("Admin"))
        assertEquals("ÉL", initialsOf("  élida   lima "))
        assertEquals("", initialsOf(" "))
    }
}
