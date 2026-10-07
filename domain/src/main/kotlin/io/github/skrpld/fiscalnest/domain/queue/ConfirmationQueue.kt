/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.queue

import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.BudgetEvent
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import io.github.skrpld.fiscalnest.domain.period.PeriodResolver
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * An occurrence of a scheduled event that is due and still waits for the user to confirm or skip
 * it. The app is not connected to a bank, so money only moves once the user confirms.
 */
data class PendingOccurrence(val event: BudgetEvent, val date: LocalDate)

/**
 * Finds the event occurrences waiting for confirmation. Stateless and safe to call from any thread.
 */
object ConfirmationQueue {
    /**
     * Occurrences dated from [AppData.queueStart] up to [today] that have no answer yet, oldest
     * first. Before the queue has started, it counts from the start of the current period.
     */
    fun pending(data: AppData, today: LocalDate): List<PendingOccurrence> {
        val start = data.queueStart ?: PeriodResolver.periodContaining(data.settings.period, today).start
        if (start > today) return emptyList()
        val handled = data.handledOccurrences.map { it.eventId to it.date }.toSet()
        return data.events
            .flatMap { event -> event.occurrencesBetween(start, today).map { PendingOccurrence(event, it) } }
            .filterNot { (it.event.id to it.date) in handled }
            .sortedWith(compareBy<PendingOccurrence> { it.date }.thenBy { it.event.name })
    }
}

/**
 * Dates of the occurrences of this event within [from]..[to], both included, in order. Follows
 * the same rules as the engine: a monthly event first occurs on the first matching day on or after
 * its start, and days past the end of a month fall on its last day.
 */
fun BudgetEvent.occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
    val lower = maxOf(from, startDate)
    val upper = endDate?.let { minOf(it, to) } ?: to
    if (lower > upper) return emptyList()
    return when (val recurrence = recurrence) {
        Recurrence.Once -> if (startDate == lower) listOf(startDate) else emptyList()
        is Recurrence.EveryNDays -> {
            val step = recurrence.days.toLong()
            val first = ceilDiv(ChronoUnit.DAYS.between(startDate, lower), step)
            generateSequence(first) { it + 1 }
                .map { startDate.plusDays(it * step) }
                .takeWhile { it <= upper }
                .toList()
        }
        is Recurrence.EveryNMonths -> {
            val step = recurrence.months.toLong()
            val startMonth = YearMonth.from(startDate)
            val anchor = if (startMonth.dayOn(recurrence.dayOfMonth) >= startDate) startMonth else startMonth.plusMonths(1)
            val first = maxOf(0L, ceilDiv(ChronoUnit.MONTHS.between(anchor, YearMonth.from(lower)), step))
            generateSequence(first) { it + 1 }
                .map { anchor.plusMonths(it * step).dayOn(recurrence.dayOfMonth) }
                .dropWhile { it < lower }
                .takeWhile { it <= upper }
                .toList()
        }
    }
}

private fun YearMonth.dayOn(dayOfMonth: Int): LocalDate = atDay(dayOfMonth.coerceIn(1, lengthOfMonth()))

private fun ceilDiv(dividend: Long, divisor: Long): Long = -Math.floorDiv(-dividend, divisor)
