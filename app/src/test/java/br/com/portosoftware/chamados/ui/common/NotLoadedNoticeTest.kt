package br.com.portosoftware.chamados.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class NotLoadedNoticeTest {
    @Test
    fun `um so de fora fica no singular`() {
        assertEquals("Mais 1 item não foi carregado. Use a busca para encontrá-lo.", itemsNotLoadedNotice(1))
        assertEquals("Mais 1 categoria não foi carregada.", categoriesNotLoadedNotice(1))
    }

    @Test
    fun `mais de um fica no plural`() {
        assertEquals("Mais 204 itens não foram carregados. Use a busca para encontrá-los.", itemsNotLoadedNotice(204))
        assertEquals("Mais 45 categorias não foram carregadas.", categoriesNotLoadedNotice(45))
    }
}
