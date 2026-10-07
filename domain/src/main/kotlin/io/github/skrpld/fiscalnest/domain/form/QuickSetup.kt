/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.form

import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.BudgetEvent
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.Limits
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Fields of the quick setup at the end of the getting started guide. */
enum class QuickSetupField { INCOME, PAY_DAY, CUSHION_CURRENT, CUSHION_TARGET }

/**
 * Validated quick setup.
 *
 * @property income monthly income, `null` when not entered
 * @property payDay day of month the income arrives, `31` meaning the last day; `null` keeps the
 * current budget period
 */
data class QuickSetupValues(
    val income: BigDecimal?,
    val payDay: Int?,
    val cushionCurrent: BigDecimal,
    val cushionTarget: BigDecimal,
)

/**
 * Text state of the quick setup. Every field is optional, but an income needs a pay day.
 */
data class QuickSetupDraft(
    val income: String = "",
    val payDay: String = "",
    val cushionCurrent: String = "",
    val cushionTarget: String = "",
) {
    fun validate(): Validation<QuickSetupValues, QuickSetupField> {
        val errors = ErrorCollector<QuickSetupField>()
        val parsedIncome = if (income.isBlank()) null else positiveAmount(income, QuickSetupField.INCOME, errors)
        val parsedPayDay = if (payDay.isBlank() && income.isBlank()) {
            null
        } else {
            intInRange(payDay, 1..LAST_DAY_OF_MONTH, QuickSetupField.PAY_DAY, errors)
        }
        val current = optionalAmount(cushionCurrent, QuickSetupField.CUSHION_CURRENT, errors)
        val target = optionalAmount(cushionTarget, QuickSetupField.CUSHION_TARGET, errors)
        return errors.result { QuickSetupValues(parsedIncome, parsedPayDay, current!!, target!!) }
    }

    private companion object {
        const val LAST_DAY_OF_MONTH = 31
    }
}

/**
 * Applies the quick setup and marks the getting started guide as finished.
 *
 * The income becomes a monthly event on the pay day, starting from the latest pay day up to
 * [today] so that the current period already counts it. The budget period then starts on the
 * pay day, capped at [Limits.MAX_MONTHLY_START_DAY].
 *
 * @param incomeId id of the income event
 * @param incomeName label of the income event, in the user's language
 */
fun AppData.applyQuickSetup(
    values: QuickSetupValues,
    incomeId: String,
    incomeName: String,
    today: LocalDate,
): AppData {
    val payDay = values.payDay
    val income = values.income
    val newEvents = if (income != null && payDay != null) {
        events + BudgetEvent(
            id = incomeId,
            name = incomeName.take(Limits.MAX_NAME_LENGTH),
            kind = EventKind.INCOME,
            amount = income,
            recurrence = Recurrence.EveryNMonths(months = 1, dayOfMonth = payDay),
            startDate = latestPayDate(today, payDay),
        )
    } else {
        events
    }
    return copy(
        events = newEvents,
        settings = settings.copy(
            period = payDay?.let { PeriodRule.Monthly(it.coerceAtMost(Limits.MAX_MONTHLY_START_DAY)) } ?: settings.period,
            cushionCurrent = values.cushionCurrent,
            cushionTarget = values.cushionTarget,
        ),
        onboardingCompleted = true,
    )
}

/**
 * The latest date on or before [today] that falls on [payDay]; days past the end of a month mean
 * its last day.
 */
fun latestPayDate(today: LocalDate, payDay: Int): LocalDate {
    fun payDateIn(month: YearMonth): LocalDate = month.atDay(payDay.coerceIn(1, month.lengthOfMonth()))
    val thisMonth = payDateIn(YearMonth.from(today))
    return if (thisMonth <= today) thisMonth else payDateIn(YearMonth.from(today).minusMonths(1))
}
