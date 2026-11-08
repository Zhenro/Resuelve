package com.resuelve.preciosve.domain.util

/**
 * Parser de números en formato venezolano/europeo ("1.234,56", "880,12", "# 1,49").
 *
 * Reglas:
 * - Si hay '.' y ',' el separador que aparece último es el decimal.
 * - Solo ',' => decimal (varias comas => separador de miles).
 * - Solo '.' => varios puntos son miles; un punto con exactamente 3 decimales y 1-3 dígitos
 *   enteros no nulos ("1.234") se interpreta como miles (convención es-VE); en otro caso decimal.
 */
object LocalizedNumberParser {

    private val NUMBER_REGEX = Regex("[0-9][0-9.,]*")

    /** Devuelve el primer número encontrado en [raw] o null si no hay ninguno válido. */
    fun parse(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val match = NUMBER_REGEX.find(raw.replace('\u00A0', ' ')) ?: return null
        val token = match.value.trimEnd('.', ',')
        if (token.isEmpty()) return null

        val lastDot = token.lastIndexOf('.')
        val lastComma = token.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastComma > lastDot) {
                    token.replace(".", "").replace(',', '.')
                } else {
                    token.replace(",", "")
                }
            lastComma >= 0 ->
                if (token.count { it == ',' } > 1) token.replace(",", "") else token.replace(',', '.')
            lastDot >= 0 -> normalizeDotOnly(token, lastDot)
            else -> token
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun normalizeDotOnly(token: String, lastDot: Int): String {
        if (token.count { it == '.' } > 1) return token.replace(".", "")
        val decimals = token.length - lastDot - 1
        val looksLikeThousands = decimals == 3 && lastDot in 1..3 && token[0] != '0'
        return if (looksLikeThousands) token.replace(".", "") else token
    }
}
