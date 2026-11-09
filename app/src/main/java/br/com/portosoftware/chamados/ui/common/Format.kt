package br.com.portosoftware.chamados.ui.common

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBR = Locale.forLanguageTag("pt-BR")
private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", ptBR)

private const val PROTOCOL_LENGTH = 8
private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val KB = 1024L
private const val MB = KB * KB
private const val PHONE_MAX_DIGITS = 11
private const val DDD_DIGITS = 2
private const val MOBILE_PREFIX = 5
private const val LANDLINE_PREFIX = 4

/** Aviso de lista cortada na primeira página, em que a busca alcança o resto. */
fun itemsNotLoadedNotice(count: Long): String =
    if (count == 1L) {
        "Mais 1 item não foi carregado. Use a busca para encontrá-lo."
    } else {
        "Mais $count itens não foram carregados. Use a busca para encontrá-los."
    }

/** Aviso de categorias cortadas na primeira página. */
fun categoriesNotLoadedNotice(count: Long): String =
    if (count == 1L) "Mais 1 categoria não foi carregada." else "Mais $count categorias não foram carregadas."

/** Protocolo curto exibido ao usuário: os 8 primeiros caracteres do UUID. */
fun protocolOf(id: String): String = "#" + id.take(PROTOCOL_LENGTH).uppercase(ptBR)

fun LocalDateTime.formatDateTime(): String = format(dateTime)

/** "agora", "há 5 min", "há 2 h", "há 3 dias". */
fun LocalDateTime.relative(now: LocalDateTime = LocalDateTime.now()): String {
    val d = Duration.between(this, now)
    return when {
        d.toMinutes() < 1 -> "agora"
        d.toMinutes() < MINUTES_PER_HOUR -> "há ${d.toMinutes()} min"
        d.toHours() < HOURS_PER_DAY -> "há ${d.toHours()} h"
        d.toDays() == 1L -> "ontem"
        else -> "há ${d.toDays()} dias"
    }
}

fun formatBytes(bytes: Long): String =
    when {
        bytes >= MB -> "%.1f MB".format(ptBR, bytes.toDouble() / MB)
        bytes >= KB -> "${bytes / KB} KB"
        else -> "$bytes B"
    }

/** Máscara de telefone do backend: (99)9999-9999 ou (99)99999-9999. */
fun maskPhone(raw: String): String {
    val d = raw.filter(Char::isDigit).take(PHONE_MAX_DIGITS)
    val ddd = d.take(DDD_DIGITS)
    val number = d.drop(DDD_DIGITS)
    val prefixLength = if (d.length == PHONE_MAX_DIGITS) MOBILE_PREFIX else LANDLINE_PREFIX
    return when {
        d.isEmpty() -> ""
        d.length <= DDD_DIGITS -> "($d"
        number.length <= prefixLength -> "($ddd)$number"
        else -> "($ddd)${number.take(prefixLength)}-${number.drop(prefixLength)}"
    }
}

/**
 * [maskPhone] para um campo editável: o cursor fica depois do mesmo dígito em que estava. Com o
 * texto reformatado e a seleção antiga, cada dígito novo entrava antes do anterior e
 * "31988887777" virava "(19)88877-7783".
 */
fun maskPhone(value: TextFieldValue): TextFieldValue {
    val masked = maskPhone(value.text)
    val digitsBeforeCursor = value.text.take(value.selection.end).count(Char::isDigit)
    var cursor = 0
    var seen = 0
    while (cursor < masked.length && seen < digitsBeforeCursor) {
        if (masked[cursor].isDigit()) seen++
        cursor++
    }
    return TextFieldValue(masked, TextRange(cursor))
}

fun String.firstName(): String = trim().substringBefore(' ')

/** Iniciais do primeiro e do último nome: "Técnico de Teste" → "TT", não "TD". */
fun initialsOf(name: String): String {
    val words = name.split(' ').filter { it.isNotBlank() }
    return listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull())
        .joinToString("") { it.first().uppercase(ptBR) }
}
