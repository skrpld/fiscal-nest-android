/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fiscalnest.core.CashReserve
import fiscalnest.core.TopupMode
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import io.github.skrpld.fiscalnest.domain.model.ThemeMode

/** Day of month that the engine treats as "last day of the month". */
private const val LAST_DAY_OF_MONTH = 31

@StringRes
fun EventKind.labelRes(): Int = when (this) {
    EventKind.INCOME -> R.string.event_kind_income
    EventKind.MANDATORY_EXPENSE -> R.string.event_kind_mandatory
    EventKind.OPTIONAL_EXPENSE -> R.string.event_kind_optional
}

@StringRes
fun EventKind.groupTitleRes(): Int = when (this) {
    EventKind.INCOME -> R.string.events_group_income
    EventKind.MANDATORY_EXPENSE -> R.string.events_group_mandatory
    EventKind.OPTIONAL_EXPENSE -> R.string.events_group_optional
}

val EventKind.icon: ImageVector
    get() = when (this) {
        EventKind.INCOME -> Icons.Rounded.AccountBalanceWallet
        EventKind.MANDATORY_EXPENSE -> Icons.Rounded.PriorityHigh
        EventKind.OPTIONAL_EXPENSE -> Icons.Rounded.ShoppingCart
    }

@StringRes
fun CashReserve.labelRes(): Int = when (this) {
    CashReserve.CUSHION_TOPUP -> R.string.reserve_cushion
    CashReserve.PIGGY_BANK -> R.string.reserve_piggy_bank
    CashReserve.UPCOMING_OPTIONAL -> R.string.reserve_upcoming_optional
}

@StringRes
fun CashReserve.descriptionRes(): Int = when (this) {
    CashReserve.CUSHION_TOPUP -> R.string.reserve_cushion_description
    CashReserve.PIGGY_BANK -> R.string.reserve_piggy_bank_description
    CashReserve.UPCOMING_OPTIONAL -> R.string.reserve_upcoming_optional_description
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
fun TopupMode.labelRes(): Int = when (this) {
    TopupMode.PERCENT_OF_TARGET -> R.string.topup_mode_target
    TopupMode.PERCENT_OF_REMAINDER -> R.string.topup_mode_remainder
}

@Composable
fun fieldErrorText(error: FieldError): String = stringResource(
    when (error) {
        FieldError.REQUIRED -> R.string.error_required
        FieldError.INVALID_NUMBER -> R.string.error_invalid_number
        FieldError.MUST_BE_POSITIVE -> R.string.error_must_be_positive
        FieldError.OUT_OF_RANGE -> R.string.error_out_of_range
        FieldError.TOO_LONG -> R.string.error_too_long
        FieldError.END_BEFORE_START -> R.string.error_end_before_start
        FieldError.IN_THE_FUTURE -> R.string.error_in_the_future
        FieldError.DUPLICATE -> R.string.error_duplicate_threshold
    },
)

@Composable
fun recurrenceText(recurrence: Recurrence): String = when (recurrence) {
    Recurrence.Once -> stringResource(R.string.recurrence_once)
    is Recurrence.EveryNDays ->
        if (recurrence.days == 1) {
            stringResource(R.string.recurrence_daily)
        } else {
            pluralStringResource(R.plurals.recurrence_every_n_days, recurrence.days, recurrence.days)
        }
    is Recurrence.EveryNMonths -> {
        val day = if (recurrence.dayOfMonth >= LAST_DAY_OF_MONTH) {
            stringResource(R.string.recurrence_last_day)
        } else {
            stringResource(R.string.recurrence_day_of_month, recurrence.dayOfMonth)
        }
        if (recurrence.months == 1) {
            stringResource(R.string.recurrence_monthly, day)
        } else {
            pluralStringResource(R.plurals.recurrence_every_n_months, recurrence.months, recurrence.months, day)
        }
    }
}

@Composable
fun periodRuleText(rule: PeriodRule): String = when (rule) {
    is PeriodRule.Monthly -> stringResource(R.string.period_monthly_summary, rule.startDay)
    is PeriodRule.FixedLength -> pluralStringResource(
        R.plurals.period_fixed_summary,
        rule.days,
        rule.days,
        LocalDateTexts.current.date(rule.anchor),
    )
}
