package br.com.portosoftware.chamados.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Quatro papéis por cor semântica, como os papéis "custom color" do Material 3. */
@Immutable
data class ColorRole(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
)

/** Semântica de status e prioridade, que não existe no ColorScheme do M3. */
@Immutable
data class ExtendedColors(
    val statusOpened: ColorRole,
    val statusTriage: ColorRole,
    val statusAssigned: ColorRole,
    val statusInProgress: ColorRole,
    val statusWaiting: ColorRole,
    val statusResolved: ColorRole,
    val statusClosed: ColorRole,
    val statusCanceled: ColorRole,
    val priorityLow: ColorRole,
    val priorityNormal: ColorRole,
    val priorityHigh: ColorRole,
    val priorityUrgent: ColorRole,
)

internal val LocalExtendedColors = staticCompositionLocalOf { lightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable @ReadOnlyComposable
    get() = LocalExtendedColors.current
