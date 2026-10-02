/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain

import fiscalnest.core.PiggyBankMode
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.BudgetEvent
import io.github.skrpld.fiscalnest.domain.model.BudgetSettings
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import io.github.skrpld.fiscalnest.domain.model.Spending
import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigDecimal
import java.time.LocalDate

fun dec(value: String): BigDecimal = BigDecimal(value)

fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

/** Asserts numeric equality, ignoring scale. */
fun assertDecimal(expected: String, actual: BigDecimal) {
    assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")
}

val salary = BudgetEvent(
    id = "salary",
    name = "Salary",
    kind = EventKind.INCOME,
    amount = dec("50000"),
    recurrence = Recurrence.EveryNMonths(months = 1, dayOfMonth = 1),
    startDate = date(2026, 1, 1),
)

val rent = BudgetEvent(
    id = "rent",
    name = "Rent",
    kind = EventKind.MANDATORY_EXPENSE,
    amount = dec("20000"),
    recurrence = Recurrence.EveryNMonths(months = 1, dayOfMonth = 5),
    startDate = date(2026, 1, 5),
)

/** The README forecast example of the engine, expressed as app data. */
val readmeData = AppData(
    events = listOf(salary, rent),
    spendings = listOf(
        Spending("s1", dec("2000"), date(2026, 8, 3), "Groceries"),
        Spending("s2", dec("1500"), date(2026, 8, 7), "Taxi"),
        Spending("old", dec("999"), date(2026, 7, 30), "Previous period"),
        Spending("future", dec("777"), date(2026, 8, 20), "Not happened yet"),
    ),
    settings = BudgetSettings(
        period = PeriodRule.Monthly(startDay = 1),
        forecastPeriods = 3,
        cushionCurrent = dec("5000"),
        cushionTarget = dec("20000"),
        piggyBankMode = PiggyBankMode.FIXED_AMOUNT,
        piggyBankTarget = dec("5000"),
        piggyBankAdmissibility = dec("0.80"),
    ),
)
