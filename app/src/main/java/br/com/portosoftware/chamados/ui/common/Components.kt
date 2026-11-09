package br.com.portosoftware.chamados.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.core.model.CallSummary
import br.com.portosoftware.chamados.core.model.DomainError
import br.com.portosoftware.chamados.ui.theme.Spacing

@Composable
fun StatusChip(
    status: CallStatus,
    modifier: Modifier = Modifier,
) {
    val c = status.colors()
    Surface(color = c.container, contentColor = c.onContainer, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(status.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(status.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PriorityBadge(
    priority: CallPriority,
    modifier: Modifier = Modifier,
) {
    val c = priority.colors()
    val urgent = priority == CallPriority.URGENT
    Surface(
        color = if (urgent) c.color else c.container,
        contentColor = if (urgent) c.onColor else c.onContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics { contentDescription = "Prioridade ${priority.label}" },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Icon(priority.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(priority.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Cartão de chamado usado em Início e Chamados. */
@Composable
fun CallCard(
    call: CallSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    protocolOf(call.id),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    call.openedAt.relative(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusChip(call.status)
                PriorityBadge(call.priority)
            }
            Text(
                call.description,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val place = listOfNotNull(call.unitName, call.localName).joinToString(" · ")
            if (place.isNotEmpty()) MetaLine(place)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    call.applicantName ?: "Solicitante não informado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TechnicianTag(call.technicianName)
            }
        }
    }
}

@Composable
private fun MetaLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun TechnicianTag(
    name: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(
            if (name == null) Icons.Outlined.PersonOff else Icons.Outlined.Person,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (name == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            name?.firstName() ?: "Sem responsável",
            style = MaterialTheme.typography.labelMedium,
            color = if (name == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Mensagem de estado vazio/erro com ação opcional. */
@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Erro padrão para qualquer [DomainError], com "Tentar novamente". */
@Composable
fun ErrorState(
    error: DomainError,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val (icon, title) =
        when (error) {
            is DomainError.Network -> Icons.Outlined.CloudOff to "Sem conexão"
            is DomainError.Forbidden -> Icons.Outlined.Lock to "Acesso restrito"
            is DomainError.NotFound -> Icons.Outlined.SearchOff to "Não encontrado"
            else -> Icons.Outlined.ErrorOutline to "Não foi possível carregar"
        }
    StateMessage(
        icon = icon,
        title = title,
        body = error.message,
        actionLabel = if (onRetry != null && error !is DomainError.Forbidden) "Tentar novamente" else null,
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
fun FullScreenState(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Faixa de aviso (offline, falha parcial) no topo do conteúdo. */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.WarningAmber,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun KpiCard(
    label: String,
    value: Long,
    icon: ImageVector,
    highlight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (highlight && value > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (highlight && value > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = container, contentColor = content),
        modifier = modifier.width(136.dp).semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(icon, contentDescription = null)
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            // Reserva duas linhas: "Sem responsável" quebra e os cards do carrossel ficariam com alturas diferentes.
            Text(label, style = MaterialTheme.typography.labelLarge, minLines = 2, maxLines = 2)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Linha "rótulo: valor" com ícone, usada no detalhe e no resumo do wizard. */
@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "Não informado", style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

/** Avatar com iniciais; vira botão para o Perfil quando a barra de navegação está oculta. */
@Composable
fun AvatarButton(
    name: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val initials = initialsOf(name)
    if (onClick != null) {
        IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = "Abrir perfil" }) { Initials(initials) }
    } else {
        Initials(initials, modifier)
    }
}

@Composable
private fun Initials(
    initials: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

// --- Skeletons ---------------------------------------------------------------------------------

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 16.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha = transition.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    Box(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .height(height)
            .alpha(alpha.value)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
fun CallCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Carregando" },
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBox(width = 96.dp, height = 14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkeletonBox(width = 88.dp, height = 24.dp)
                SkeletonBox(width = 72.dp, height = 24.dp)
            }
            SkeletonBox(height = 16.dp)
            SkeletonBox(width = 200.dp, height = 16.dp)
        }
    }
}

@Composable
fun ClickableText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(Spacing.sm),
    )
}
