package br.com.portosoftware.chamados.ui.common

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MaskPhoneTest {
    /** Digita um caractere por vez na posição do cursor, partindo sempre do valor já mascarado. */
    private fun type(
        text: String,
        start: TextFieldValue = TextFieldValue(),
    ): TextFieldValue =
        text.fold(start) { field, c ->
            val cursor = field.selection.end
            maskPhone(TextFieldValue(field.text.take(cursor) + c + field.text.drop(cursor), TextRange(cursor + 1)))
        }

    @Test
    fun `digitar um celular forma a mascara na ordem com o cursor no fim`() {
        val result = type("31988887777")
        assertEquals("(31)98888-7777", result.text)
        assertEquals(TextRange(result.text.length), result.selection)
    }

    @Test
    fun `digitar um fixo usa quatro digitos no prefixo`() {
        assertEquals("(31)3333-1234", type("3133331234").text)
    }

    @Test
    fun `apagar no fim tira o ultimo digito e o hifen`() {
        val deleted = maskPhone(TextFieldValue("(31)3333-", TextRange(9)))
        assertEquals("(31)3333", deleted.text)
        assertEquals(TextRange(8), deleted.selection)
    }

    @Test
    fun `inserir no meio deixa o cursor depois do digito inserido`() {
        val result = type("9", start = TextFieldValue("(31)3333-1234", TextRange(4)))
        assertEquals("(31)93333-1234", result.text)
        assertEquals(TextRange(5), result.selection)
    }
}
