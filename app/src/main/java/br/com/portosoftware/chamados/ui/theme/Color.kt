package br.com.portosoftware.chamados.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Gerado com material-color-utilities (SchemeFidelity).
// Claro: seed cobre #C36F3C (marca do tema marfim do web).
// Escuro: seed azul #3B82F6 (o web escuro mantém o azul original).
// Cores estendidas de status/prioridade usam blend=false, iguais às variáveis do design.

internal val lightScheme =
    lightColorScheme(
        primary = Color(0xFF914817),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFAF5F2E),
        onPrimaryContainer = Color(0xFFFFFBFF),
        inversePrimary = Color(0xFFFFB68E),
        secondary = Color(0xFF7D553F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFC9AD),
        onSecondaryContainer = Color(0xFF7A523C),
        tertiary = Color(0xFF00666F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF00818C),
        onTertiaryContainer = Color(0xFFF6FEFF),
        background = Color(0xFFFFF8F6),
        onBackground = Color(0xFF221A16),
        surface = Color(0xFFFFF8F6),
        onSurface = Color(0xFF221A16),
        surfaceVariant = Color(0xFFF7DDD1),
        onSurfaceVariant = Color(0xFF54433A),
        surfaceTint = Color(0xFF944A1A),
        inverseSurface = Color(0xFF382E2A),
        inverseOnSurface = Color(0xFFFEEDE6),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF877369),
        outlineVariant = Color(0xFFDAC2B6),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFF8F6),
        surfaceContainer = Color(0xFFFBEBE3),
        surfaceContainerHigh = Color(0xFFF5E5DE),
        surfaceContainerHighest = Color(0xFFEFDFD8),
        surfaceContainerLow = Color(0xFFFFF1EB),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFE7D7D0),
    )

internal val darkScheme =
    darkColorScheme(
        primary = Color(0xFFADC6FF),
        onPrimary = Color(0xFF002E6A),
        primaryContainer = Color(0xFF4D8EFF),
        onPrimaryContainer = Color(0xFF001C46),
        inversePrimary = Color(0xFF005AC2),
        secondary = Color(0xFFB1C6F9),
        onSecondary = Color(0xFF182F59),
        secondaryContainer = Color(0xFF304671),
        onSecondaryContainer = Color(0xFF9FB5E7),
        tertiary = Color(0xFFFFB786),
        onTertiary = Color(0xFF502400),
        tertiaryContainer = Color(0xFFDF7412),
        onTertiaryContainer = Color(0xFF351600),
        background = Color(0xFF10131A),
        onBackground = Color(0xFFE1E2EC),
        surface = Color(0xFF10131A),
        onSurface = Color(0xFFE1E2EC),
        surfaceVariant = Color(0xFF424754),
        onSurfaceVariant = Color(0xFFC2C6D6),
        surfaceTint = Color(0xFFADC6FF),
        inverseSurface = Color(0xFFE1E2EC),
        inverseOnSurface = Color(0xFF2E3038),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8C909F),
        outlineVariant = Color(0xFF424754),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF363941),
        surfaceContainer = Color(0xFF1D2027),
        surfaceContainerHigh = Color(0xFF272A31),
        surfaceContainerHighest = Color(0xFF32353C),
        surfaceContainerLow = Color(0xFF191B23),
        surfaceContainerLowest = Color(0xFF0B0E15),
        surfaceDim = Color(0xFF10131A),
    )

internal val lightExtendedColors =
    ExtendedColors(
        statusOpened = ColorRole(Color(0xFF005AC2), Color(0xFFFFFFFF), Color(0xFFD8E2FF), Color(0xFF001A42)),
        statusTriage = ColorRole(Color(0xFF6D3BD7), Color(0xFFFFFFFF), Color(0xFFE9DDFF), Color(0xFF23005C)),
        statusAssigned = ColorRole(Color(0xFF00687A), Color(0xFFFFFFFF), Color(0xFFACEDFF), Color(0xFF001F26)),
        statusInProgress = ColorRole(Color(0xFF855300), Color(0xFFFFFFFF), Color(0xFFFFDDB8), Color(0xFF2A1700)),
        statusWaiting = ColorRole(Color(0xFFBA0060), Color(0xFFFFFFFF), Color(0xFFFFD9E2), Color(0xFF3F001C)),
        statusResolved = ColorRole(Color(0xFF006C49), Color(0xFFFFFFFF), Color(0xFF6FFBBE), Color(0xFF002113)),
        statusClosed = ColorRole(Color(0xFF5A5F66), Color(0xFFFFFFFF), Color(0xFFDFE2EB), Color(0xFF181C22)),
        statusCanceled = ColorRole(Color(0xFFB91A24), Color(0xFFFFFFFF), Color(0xFFFFDAD7), Color(0xFF410004)),
        priorityLow = ColorRole(Color(0xFF006591), Color(0xFFFFFFFF), Color(0xFFC9E6FF), Color(0xFF001E2F)),
        priorityNormal = ColorRole(Color(0xFF006C49), Color(0xFFFFFFFF), Color(0xFF6FFBBE), Color(0xFF002113)),
        priorityHigh = ColorRole(Color(0xFF855300), Color(0xFFFFFFFF), Color(0xFFFFDDB8), Color(0xFF2A1700)),
        priorityUrgent = ColorRole(Color(0xFFBF0715), Color(0xFFFFFFFF), Color(0xFFFFDAD6), Color(0xFF410002)),
    )

internal val darkExtendedColors =
    ExtendedColors(
        statusOpened = ColorRole(Color(0xFFADC6FF), Color(0xFF002E6A), Color(0xFF004395), Color(0xFFD8E2FF)),
        statusTriage = ColorRole(Color(0xFFD0BCFF), Color(0xFF3C0091), Color(0xFF5516BE), Color(0xFFE9DDFF)),
        statusAssigned = ColorRole(Color(0xFF4CD7F6), Color(0xFF003640), Color(0xFF004E5C), Color(0xFFACEDFF)),
        statusInProgress = ColorRole(Color(0xFFFFB95F), Color(0xFF472A00), Color(0xFF653E00), Color(0xFFFFDDB8)),
        statusWaiting = ColorRole(Color(0xFFFFB1C7), Color(0xFF650031), Color(0xFF8E0048), Color(0xFFFFD9E2)),
        statusResolved = ColorRole(Color(0xFF4EDEA3), Color(0xFF003824), Color(0xFF005236), Color(0xFF6FFBBE)),
        statusClosed = ColorRole(Color(0xFFC3C6CF), Color(0xFF2C3137), Color(0xFF43474E), Color(0xFFDFE2EB)),
        statusCanceled = ColorRole(Color(0xFFFFB3AD), Color(0xFF68000A), Color(0xFF930013), Color(0xFFFFDAD7)),
        priorityLow = ColorRole(Color(0xFF89CEFF), Color(0xFF00344D), Color(0xFF004C6E), Color(0xFFC9E6FF)),
        priorityNormal = ColorRole(Color(0xFF4EDEA3), Color(0xFF003824), Color(0xFF005236), Color(0xFF6FFBBE)),
        priorityHigh = ColorRole(Color(0xFFFFB95F), Color(0xFF472A00), Color(0xFF653E00), Color(0xFFFFDDB8)),
        priorityUrgent = ColorRole(Color(0xFFFFB4AB), Color(0xFF690005), Color(0xFF93000B), Color(0xFFFFDAD6)),
    )
