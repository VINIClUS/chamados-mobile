package br.com.portosoftware.chamados.ui.newcall

import br.com.portosoftware.chamados.core.model.CallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewCallValidationTest {
    private val applicant = DraftApplicant("ap-1", "Maria")
    private val complete =
        NewCallDraft(
            applicant = applicant,
            unitId = "un",
            localId = "lo",
            categoryId = "cat",
            subcategoryId = "sub",
            description = "Computador não liga desde cedo",
        )

    @Test
    fun `cada etapa exige seus campos`() {
        assertEquals(setOf("applicant"), NewCallViewModel.validate(WizardStep.APPLICANT, NewCallDraft()).keys)
        assertEquals(setOf("unitId", "localId"), NewCallViewModel.validate(WizardStep.LOCATION, NewCallDraft()).keys)
        assertEquals(
            setOf("categoryId", "description"),
            NewCallViewModel.validate(WizardStep.PROBLEM, NewCallDraft(description = "curta")).keys,
        )
        // O backend exige o sintoma junto com a categoria.
        assertEquals(setOf("subcategoryId"), NewCallViewModel.validate(WizardStep.PROBLEM, complete.copy(subcategoryId = null)).keys)
    }

    @Test
    fun `rascunho completo passa na revisao`() {
        assertTrue(NewCallViewModel.validate(WizardStep.REVIEW, complete).isEmpty())
    }

    @Test
    fun `fechado na criacao nao pede relato, que o backend nao recebe`() {
        val closed = complete.copy(status = CallStatus.CLOSED)
        assertTrue(NewCallViewModel.validate(WizardStep.REVIEW, closed).isEmpty())
    }

    @Test
    fun `nome do cargo novo para no limite do backend`() {
        val form = QuickApplicantForm().withNewPositionName("x".repeat(151))
        assertEquals(150, form.newPositionName.length)
    }
}
