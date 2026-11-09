package br.com.portosoftware.chamados.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import br.com.portosoftware.chamados.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Jornada principal do técnico, com a navegação real e os repositórios fake via Hilt:
 * Login → Início → Chamados → Detalhe → Atender → volta ao Detalhe com snackbar.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class MainJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hilt.inject()

    private fun waitForText(
        text: String,
        substring: Boolean = false,
    ) = compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeoutMillis = 10_000)

    @Test
    fun leitorSoTemOPerfilComOAviso() {
        waitForText("Entrar")
        compose.onNodeWithText("E-mail").performTextInput("leitor@chamados.test")
        compose.onNodeWithText("Senha").performTextInput("demo")
        compose.onNodeWithText("Entrar").performClick()

        waitForText("Escolha a secretaria", substring = true)
        compose.onAllNodesWithText("Saúde", substring = true).onFirst().performClick()
        compose.onNode(hasText("Continuar") and hasClickAction()).performClick()

        waitForText("só para relatórios", substring = true)
        compose.onNodeWithText("Vera Auditoria").assertExists()
        compose.onNodeWithText("Chamados").assertDoesNotExist()
        compose.onNodeWithText("Estoque").assertDoesNotExist()
        compose.onNodeWithContentDescription("Voltar").assertDoesNotExist()
    }

    @Test
    fun tecnicoAtendeUmChamadoDoInicioAoHistorico() {
        waitForText("Entrar")
        compose.onNodeWithText("E-mail").performTextInput("tecnico@chamados.test")
        compose.onNodeWithText("Senha").performTextInput("demo")
        compose.onNodeWithText("Entrar").performClick()

        // Início ("Meu turno") com indicadores e recentes.
        waitForText("Olá, Carlos")
        waitForText("Urgentes")
        compose.onNodeWithText("Ver todos").performClick()

        // Chamados: lista paginada.
        waitForText("Buscar por descrição", substring = true)
        waitForText("Sistema de farmácia", substring = true)
        compose.onAllNodesWithText("Sistema de farmácia", substring = true).onFirst().performClick()

        // Detalhe.
        waitForText("Histórico")
        compose.onNodeWithText("Atender").performClick()

        // Atender.
        waitForText("Status atual") // formulário carregado (o botão aparece antes, já na barra inferior)
        compose.onNodeWithText("O que foi feito *").performTextInput("Reinstalei o cliente e testei com a farmacêutica.")
        compose.onNodeWithText("Fechado").performScrollTo().performClick()
        compose.onNode(hasText("Registrar atendimento") and hasClickAction()).performClick()

        // De volta ao Detalhe, com o resultado.
        waitForText("Atendimento registrado.")
        waitForText("Reinstalei o cliente", substring = true)

        // A lista recarrega ao voltar (o evento chega com a lista fora da composição).
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.waitUntilAtLeastOneExists(
            hasText("Sistema de farmácia", substring = true) and hasText("Fechado"),
            timeoutMillis = 10_000,
        )
    }
}
