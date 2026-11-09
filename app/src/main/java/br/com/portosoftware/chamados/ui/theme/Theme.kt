package br.com.portosoftware.chamados.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import br.com.portosoftware.chamados.core.model.ThemeMode

private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/**
 * Tema do app. Dynamic color fica desligado de propósito: a semântica de status
 * depende das cores estendidas e a marca da prefeitura precisa ser reconhecível.
 * A tipografia é a escala M3 padrão em Roboto, a fonte do sistema.
 */
@Composable
fun ChamadosTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    // enableEdgeToEdge escolhe a cor dos ícones pelo tema do sistema; com tema claro/escuro
    // forçado no app, os ícones ficariam invisíveis sobre o fundo.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    CompositionLocalProvider(LocalExtendedColors provides if (dark) darkExtendedColors else lightExtendedColors) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Espaçamentos da grade de 4 dp (mesmos tokens do grupo space do design). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val screen = 16.dp
}
