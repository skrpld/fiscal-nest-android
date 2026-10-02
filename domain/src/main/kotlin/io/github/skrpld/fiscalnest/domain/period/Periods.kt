/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.period

import io.github.skrpld.fiscalnest.domain.model.Limits
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * An inclusive range of dates.
 */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(start <= endInclusive) { "start must not be after endInclusive" }
    }

    /** Number of days in the range, both ends included. */
    val lengthInDays: Int get() = Math.toIntExact(ChronoUnit.DAYS.between(start, endInclusive) + 1)

    operator fun contains(date: LocalDate): Boolean = date >= start && date <= endInclusive
}

/**
 * Finds the budget period a date belongs to.
 */
object PeriodResolver {
    /**
     * Returns the period of [rule] that contains [date].
     *
     * A monthly period ends the day before the same day of the next month, so the engine chains
     * the following periods by calendar months. A fixed-length period that happens to span exactly
     * one calendar month is chained by months too: the engine cannot tell the two apart.
     */
    fun periodContaining(rule: PeriodRule, date: LocalDate): DateRange = when (rule) {
        is PeriodRule.Monthly -> {
            val day = rule.startDay.coerceIn(1, Limits.MAX_MONTHLY_START_DAY)
            val sameMonth = date.withDayOfMonth(day)
            val start = if (date.dayOfMonth >= day) sameMonth else sameMonth.minusMonths(1)
            DateRange(start, start.plusMonths(1).minusDays(1))
        }
        is PeriodRule.FixedLength -> {
            val length = rule.days.coerceIn(1, Limits.MAX_PERIOD_DAYS).toLong()
            val index = Math.floorDiv(ChronoUnit.DAYS.between(rule.anchor, date), length)
            val start = rule.anchor.plusDays(index * length)
            DateRange(start, start.plusDays(length - 1))
        }
    }
}
