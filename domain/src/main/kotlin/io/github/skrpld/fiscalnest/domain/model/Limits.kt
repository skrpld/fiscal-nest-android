/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.model

import java.math.BigDecimal

/**
 * Bounds of user input. The engine accepts far larger values; these keep the UI readable and the
 * forecast cheap, and reject garbage from imported backups.
 */
object Limits {
    const val MAX_INTEGER_DIGITS: Int = 13
    const val MAX_FRACTION_DIGITS: Int = 4
    const val MAX_NAME_LENGTH: Int = 60
    const val MAX_NOTE_LENGTH: Int = 120
    const val MIN_FORECAST_PERIODS: Int = 1
    const val MAX_FORECAST_PERIODS: Int = 24
    const val MAX_RECURRENCE_DAYS: Int = 3660
    const val MAX_RECURRENCE_MONTHS: Int = 120
    const val MAX_MONTHLY_START_DAY: Int = 28
    const val MAX_PERIOD_DAYS: Int = 366
    const val MAX_CUSHION_LEVELS: Int = 10
    val MONEY_SCALES: List<Int> = listOf(0, 2)

    /** `true` for a non-negative amount within the supported number of digits. */
    fun isValidAmount(value: BigDecimal): Boolean =
        value.signum() >= 0 && integerDigits(value) <= MAX_INTEGER_DIGITS && value.scale() <= MAX_FRACTION_DIGITS + 2

    /** `true` for a ratio in `0..1`. */
    fun isValidRatio(value: BigDecimal): Boolean =
        value.signum() >= 0 && value <= BigDecimal.ONE && value.scale() <= MAX_FRACTION_DIGITS + 2

    private fun integerDigits(value: BigDecimal): Long = value.precision().toLong() - value.scale()
}

/**
 * Checks stored or imported data against [Limits] and the rules of the engine.
 */
object AppDataValidator {
    /**
     * Returns a description of every problem found; empty when [data] is valid.
     */
    fun problems(data: AppData): List<String> = buildList {
        if (data.schemaVersion > AppData.SCHEMA_VERSION) add("Unsupported schema version ${data.schemaVersion}")
        val ids = data.events.map { it.id }
        if (ids.size != ids.toSet().size) add("Duplicate event ids")
        data.events.forEach { event ->
            if (event.name.length > Limits.MAX_NAME_LENGTH) add("Event name too long: ${event.id}")
            if (!Limits.isValidAmount(event.amount)) add("Invalid event amount: ${event.id}")
            if (event.endDate != null && event.endDate < event.startDate) add("Event ends before it starts: ${event.id}")
            when (val recurrence = event.recurrence) {
                Recurrence.Once -> Unit
                is Recurrence.EveryNDays ->
                    if (recurrence.days !in 1..Limits.MAX_RECURRENCE_DAYS) add("Invalid day step: ${event.id}")
                is Recurrence.EveryNMonths -> {
                    if (recurrence.months !in 1..Limits.MAX_RECURRENCE_MONTHS) add("Invalid month step: ${event.id}")
                    if (recurrence.dayOfMonth !in 1..31) add("Invalid day of month: ${event.id}")
                }
            }
        }
        data.spendings.forEach { spending ->
            if (!Limits.isValidAmount(spending.amount)) add("Invalid spending amount: ${spending.id}")
            if (spending.note.length > Limits.MAX_NOTE_LENGTH) add("Spending note too long: ${spending.id}")
        }
        addAll(settingsProblems(data.settings))
    }

    private fun settingsProblems(settings: BudgetSettings): List<String> = buildList {
        when (val period = settings.period) {
            is PeriodRule.Monthly ->
                if (period.startDay !in 1..Limits.MAX_MONTHLY_START_DAY) add("Invalid period start day")
            is PeriodRule.FixedLength ->
                if (period.days !in 1..Limits.MAX_PERIOD_DAYS) add("Invalid period length")
        }
        if (settings.forecastPeriods !in Limits.MIN_FORECAST_PERIODS..Limits.MAX_FORECAST_PERIODS) {
            add("Invalid forecast horizon")
        }
        if (!Limits.isValidAmount(settings.cushionCurrent)) add("Invalid cushion balance")
        if (!Limits.isValidAmount(settings.cushionTarget)) add("Invalid cushion target")
        if (!Limits.isValidAmount(settings.piggyBankTarget)) add("Invalid piggy bank target")
        if (settings.piggyBankMode == fiscalnest.core.PiggyBankMode.PERCENT_OF_REMAINDER &&
            !Limits.isValidRatio(settings.piggyBankTarget)
        ) {
            add("Invalid piggy bank ratio")
        }
        if (!Limits.isValidRatio(settings.piggyBankAdmissibility)) add("Invalid piggy bank admissibility")
        if (settings.moneyScale !in Limits.MONEY_SCALES) add("Invalid money scale")
        if (settings.cushionLevels.isEmpty() || settings.cushionLevels.size > Limits.MAX_CUSHION_LEVELS) {
            add("Invalid number of cushion levels")
        }
        settings.cushionLevels.forEach { level ->
            if (level.name.length > Limits.MAX_NAME_LENGTH) add("Cushion level name too long")
            if (!Limits.isValidRatio(level.maxFillRatio) || level.maxFillRatio.signum() == 0) {
                add("Invalid cushion level threshold")
            }
            if (!Limits.isValidRatio(level.topupRatio)) add("Invalid cushion level top-up")
            if (!Limits.isValidRatio(level.admissibilityRatio)) add("Invalid cushion level admissibility")
        }
        val thresholds = settings.cushionLevels.map { it.maxFillRatio.stripTrailingZeros() }
        if (thresholds.size != thresholds.toSet().size) add("Duplicate cushion level thresholds")
        val code = settings.currencyCode
        if (code != null && runCatching { java.util.Currency.getInstance(code) }.isFailure) {
            add("Unknown currency $code")
        }
    }
}
