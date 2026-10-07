/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.input

import io.github.skrpld.fiscalnest.domain.model.Limits
import java.math.BigDecimal

/**
 * Parses numbers typed by the user, whatever their locale conventions.
 *
 * Spaces, no-break spaces, apostrophes and underscores are digit grouping. When both `.` and `,`
 * appear, the last one is the decimal separator. A single `.` or `,` is the decimal separator;
 * several identical ones are grouping (`1,234,567`). Digits of other scripts, such as Arabic-Indic
 * `٣`, and the Arabic separators `٫` and `٬` are accepted too.
 */
object DecimalInput {
    private val ignored = setOf(' ', ' ', ' ', '\'', '_', '’', ARABIC_GROUPING)
    private val digits = Regex("\\d*\\.?\\d*")

    /**
     * Returns the non-negative value of [text], or `null` when it is blank, malformed, negative or
     * longer than [Limits] allow.
     */
    fun parse(text: String): BigDecimal? {
        val compact = text.trim().filterNot { it in ignored }.map(::toAsciiDigit).joinToString("")
        if (compact.isEmpty()) return null
        val normalized = normalizeSeparators(compact) ?: return null
        if (normalized.isEmpty() || normalized == "." || !digits.matches(normalized)) return null
        val integerPart = normalized.substringBefore('.').trimStart('0')
        val fractionPart = normalized.substringAfter('.', missingDelimiterValue = "")
        if (integerPart.length > Limits.MAX_INTEGER_DIGITS || fractionPart.length > Limits.MAX_FRACTION_DIGITS) return null
        val canonical = (if (normalized.startsWith('.')) "0$normalized" else normalized).trimEnd('.')
        return BigDecimal(canonical)
    }

    /**
     * Parses a percentage in `0..100` and returns it as a ratio in `0..1`.
     */
    fun parsePercent(text: String): BigDecimal? {
        val value = parse(text) ?: return null
        if (value > HUNDRED) return null
        return value.movePointLeft(2)
    }

    /** Formats an amount for an input field: no grouping, no trailing zeros. */
    fun formatAmount(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    /** Formats a ratio as a percentage for an input field: `0.30` gives `30`. */
    fun formatPercent(ratio: BigDecimal): String = ratio.movePointRight(2).stripTrailingZeros().toPlainString()

    private fun normalizeSeparators(text: String): String? {
        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')
        return when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimal = if (lastDot > lastComma) '.' else ','
                val grouping = if (decimal == '.') ',' else '.'
                if (text.count { it == decimal } > 1) return null
                text.replace(grouping.toString(), "").replace(decimal, '.')
            }
            lastComma >= 0 -> if (text.count { it == ',' } > 1) text.replace(",", "") else text.replace(',', '.')
            lastDot >= 0 -> if (text.count { it == '.' } > 1) text.replace(".", "") else text
            else -> text
        }
    }

    private fun toAsciiDigit(char: Char): Char = when {
        char == ARABIC_DECIMAL -> '.'
        char in '0'..'9' || !Character.isDigit(char) -> char
        else -> '0' + Character.digit(char, 10)
    }

    private val HUNDRED = BigDecimal(100)
    private const val ARABIC_DECIMAL = '\u066B'
    private const val ARABIC_GROUPING = '\u066C'
}
