package br.com.portosoftware.chamados.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FiberNew
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.ui.theme.ColorRole
import br.com.portosoftware.chamados.ui.theme.extendedColors

// Cor nunca é o único sinal: todo status e prioridade tem ícone e texto.

@Composable
@ReadOnlyComposable
fun CallStatus.colors(): ColorRole {
    val c = MaterialTheme.extendedColors
    return when (this) {
        CallStatus.OPENED -> c.statusOpened
        CallStatus.IN_PROGRESS -> c.statusInProgress
        CallStatus.CLOSED, CallStatus.UNKNOWN -> c.statusClosed
    }
}

val CallStatus.icon: ImageVector
    get() =
        when (this) {
            CallStatus.OPENED -> Icons.Outlined.FiberNew
            CallStatus.IN_PROGRESS -> Icons.Outlined.Build
            CallStatus.CLOSED -> Icons.Outlined.Lock
            CallStatus.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
        }

@Composable
@ReadOnlyComposable
fun CallPriority.colors(): ColorRole {
    val c = MaterialTheme.extendedColors
    return when (this) {
        CallPriority.LOW -> c.priorityLow
        CallPriority.NORMAL, CallPriority.UNKNOWN -> c.priorityNormal
        CallPriority.HIGH -> c.priorityHigh
        CallPriority.URGENT -> c.priorityUrgent
    }
}

val CallPriority.icon: ImageVector
    get() =
        when (this) {
            CallPriority.LOW -> Icons.Outlined.KeyboardArrowDown
            CallPriority.NORMAL -> Icons.Outlined.Remove
            CallPriority.HIGH -> Icons.Outlined.KeyboardArrowUp
            CallPriority.URGENT -> Icons.Outlined.PriorityHigh
            CallPriority.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
        }
