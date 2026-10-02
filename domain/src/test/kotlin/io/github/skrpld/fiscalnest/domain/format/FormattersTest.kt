/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.format

import io.github.skrpld.fiscalnest.domain.dec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class FormattersTest {
    @Test
    fun `formats money in the locale currency`() {
        val formatter = MoneyFormatter(Locale.US, currencyCode = null, scale = 2)
        assertEquals("USD", formatter.currency.currencyCode)
        assertEquals("$1,234.50", formatter.format(dec("1234.5")))
        assertEquals("+$10.00", formatter.formatSigned(dec("10")))
        assertEquals("1,234.50", formatter.formatPlain(dec("1234.5")))
    }

    @Test
    fun `formats money in a chosen currency without decimals`() {
        val formatter = MoneyFormatter(Locale.forLanguageTag("ru-RU"), currencyCode = "RUB", scale = 0)
        val text = formatter.format(dec("1234.5"))
        assertTrue(text.filter { it.isDigit() } == "1235", text)
        assertTrue(formatter.symbol.isNotBlank())
    }

    @Test
    fun `falls back when the locale has no country`() {
        assertEquals("USD", MoneyFormatter.localeCurrency(Locale.forLanguageTag("ru")).currencyCode)
        assertEquals("EUR", MoneyFormatter(Locale.forLanguageTag("ru"), "EUR", 2).currency.currencyCode)
        assertEquals("USD", MoneyFormatter(Locale.US, "NOPE", 2).currency.currencyCode)
    }

    @Test
    fun `formats ratios as percentages`() {
        val formatter = PercentFormatter(Locale.US)
        assertEquals("25%", formatter.format(dec("0.2500")))
        assertEquals("12.5%", formatter.format(dec("0.125")))
    }
}
