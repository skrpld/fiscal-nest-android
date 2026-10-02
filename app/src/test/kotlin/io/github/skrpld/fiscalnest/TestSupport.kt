/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest

import fiscalnest.core.PiggyBankMode
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.BudgetEvent
import io.github.skrpld.fiscalnest.domain.model.BudgetSettings
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Replaces the main dispatcher, which view models use, with a test dispatcher.
 */
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/** 7 August 2026, the "today" of the engine README forecast example. */
val Today: LocalDate = LocalDate.of(2026, 8, 7)

val FixedToday: DateProvider = DateProvider.fixed(Today)

/** Generates `id-1`, `id-2`, ... */
fun sequentialIds(): IdGenerator {
    var next = 0
    return IdGenerator { "id-${++next}" }
}

val Salary = BudgetEvent(
    id = "salary",
    name = "Salary",
    kind = EventKind.INCOME,
    amount = BigDecimal("50000"),
    recurrence = Recurrence.EveryNMonths(months = 1, dayOfMonth = 1),
    startDate = LocalDate.of(2026, 1, 1),
)

val Rent = BudgetEvent(
    id = "rent",
    name = "Rent",
    kind = EventKind.MANDATORY_EXPENSE,
    amount = BigDecimal("20000"),
    recurrence = Recurrence.EveryNMonths(months = 1, dayOfMonth = 5),
    startDate = LocalDate.of(2026, 1, 5),
)

/** The engine README example: salary on the 1st, rent on the 5th, cushion 5 000 of 20 000. */
fun sampleData(): AppData = AppData(
    events = listOf(Salary, Rent),
    settings = BudgetSettings(
        forecastPeriods = 3,
        cushionCurrent = BigDecimal("5000"),
        cushionTarget = BigDecimal("20000"),
        piggyBankMode = PiggyBankMode.FIXED_AMOUNT,
        piggyBankTarget = BigDecimal("5000"),
        piggyBankAdmissibility = BigDecimal("0.80"),
    ),
)

/** Asserts numeric equality, ignoring scale. */
fun assertDecimal(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
}
