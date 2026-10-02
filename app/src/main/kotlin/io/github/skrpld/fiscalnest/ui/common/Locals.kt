/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.skrpld.fiscalnest.AppContainer
import io.github.skrpld.fiscalnest.domain.format.MoneyFormatter
import io.github.skrpld.fiscalnest.domain.format.PercentFormatter
import io.github.skrpld.fiscalnest.domain.period.DateRange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** App dependencies for creating view models. Provided by the activity. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }

/** Money formatter for the user's locale, currency and rounding. */
val LocalMoneyFormatter = staticCompositionLocalOf { MoneyFormatter(Locale.getDefault(), null, 2) }

/** Percentage formatter for the user's locale. */
val LocalPercentFormatter = staticCompositionLocalOf { PercentFormatter(Locale.getDefault()) }

/** Date formatter for the user's locale. */
val LocalDateTexts = staticCompositionLocalOf { DateTexts(Locale.getDefault()) }

/**
 * Creates or returns the [ViewModel] of the current navigation entry, built from the app's
 * dependencies.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key) { create(container) }
}

/**
 * Locale-aware date texts.
 */
class DateTexts(locale: Locale) {
    private val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)

    /** `2 Oct 2026` / `2 окт. 2026 г.`. */
    fun date(date: LocalDate): String = medium.format(date)

    /** `2 Oct` / `2 окт.`. */
    fun dayMonth(date: LocalDate): String = dayMonth.format(date)

    /** `1 Oct – 31 Oct 2026`. */
    fun range(start: LocalDate, endInclusive: LocalDate): String = "${dayMonth(start)} – ${date(endInclusive)}"

    fun range(range: DateRange): String = range(range.start, range.endInclusive)
}
