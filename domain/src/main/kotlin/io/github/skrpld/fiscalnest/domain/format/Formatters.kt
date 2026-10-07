/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats money in the user's locale and currency. Not thread-safe: create one per UI thread.
 *
 * @param currencyCode ISO 4217 code; `null` uses the currency of [locale]
 * @param scale decimal places shown
 */
class MoneyFormatter(
    locale: Locale,
    currencyCode: String?,
    private val scale: Int,
) {
    /** The currency amounts are shown in. */
    val currency: Currency = resolveCurrency(currencyCode, locale)

    /** The currency symbol in [locale], e.g. `₽` or `$`. */
    val symbol: String = currency.getSymbol(locale)

    private val currencyFormat = (NumberFormat.getCurrencyInstance(locale)).apply {
        currency = this@MoneyFormatter.currency
        minimumFractionDigits = scale
        maximumFractionDigits = scale
        roundingMode = RoundingMode.HALF_UP
    }

    private val plainFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = scale
        maximumFractionDigits = scale
        roundingMode = RoundingMode.HALF_UP
    }

    /** `1 234,50 ₽` / `$1,234.50`. */
    fun format(amount: BigDecimal): String = currencyFormat.format(amount)

    /** Like [format], with an explicit `+` for positive amounts. */
    fun formatSigned(amount: BigDecimal): String =
        if (amount.signum() > 0) "+" + format(amount) else format(amount)

    /** The number without the currency: `1 234,50`. */
    fun formatPlain(amount: BigDecimal): String = plainFormat.format(amount)

    companion object {
        /** Codes offered in the currency picker, after the locale's own currency. */
        val COMMON_CURRENCIES: List<String> = listOf(
            "RUB", "USD", "EUR", "KZT", "BYN", "UAH", "UZS", "KGS", "AMD", "GEL", "AZN", "TRY",
            "GBP", "CHF", "PLN", "CZK", "CNY", "JPY", "INR", "AED", "SAR", "EGP", "ILS", "BRL", "MXN", "CAD", "AUD",
        )

        /** The currency of [locale], or US dollars when the locale has no country. */
        fun localeCurrency(locale: Locale): Currency =
            runCatching { Currency.getInstance(locale) }.getOrNull() ?: Currency.getInstance("USD")

        private fun resolveCurrency(code: String?, locale: Locale): Currency =
            code?.let { runCatching { Currency.getInstance(it) }.getOrNull() } ?: localeCurrency(locale)
    }
}

/**
 * Formats ratios on the `0..1` scale as percentages.
 */
class PercentFormatter(locale: Locale) {
    private val format = NumberFormat.getPercentInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
        roundingMode = RoundingMode.HALF_UP
    }

    /** `0.255` gives `25.5%` (`25,5 %` in Russian). */
    fun format(ratio: BigDecimal): String = format.format(ratio)
}
