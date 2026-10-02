/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.engine

import fiscalnest.core.CriticalityLevel
import fiscalnest.core.EngineConfig
import fiscalnest.core.EventRecurrence
import fiscalnest.core.ExpenseEvent
import fiscalnest.core.IncomeEvent
import io.github.skrpld.fiscalnest.domain.model.BudgetEvent
import io.github.skrpld.fiscalnest.domain.model.BudgetSettings
import io.github.skrpld.fiscalnest.domain.model.CushionLevel
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import java.math.RoundingMode

/** Decimal places of reported ratios: `0.2500` shows as `25.00%`. */
const val PERCENTAGE_SCALE: Int = 4

/**
 * Builds the engine configuration.
 *
 * @throws IllegalArgumentException if the settings break an engine rule
 */
fun BudgetSettings.toEngineConfig(): EngineConfig = EngineConfig(
    roundingMode = RoundingMode.HALF_UP,
    moneyScale = moneyScale,
    percentageScale = PERCENTAGE_SCALE,
    criticalityLevels = cushionLevels.sortedBy { it.maxFillRatio }.map { it.toEngine() },
    piggyBankMode = piggyBankMode,
    piggyBankTarget = piggyBankTarget,
    piggyBankAdmissibilityPct = piggyBankAdmissibility,
    cashReserves = cashReserves,
)

/**
 * @throws IllegalArgumentException if a ratio is out of range
 */
fun CushionLevel.toEngine(): CriticalityLevel = CriticalityLevel(
    name = name,
    maxFillPct = maxFillRatio,
    topupMode = topupMode,
    topupValue = topupRatio,
    admissibilityPct = admissibilityRatio,
)

/**
 * @throws IllegalArgumentException if a recurrence parameter is out of range
 */
fun Recurrence.toEngine(): EventRecurrence = when (this) {
    Recurrence.Once -> EventRecurrence.OneTime
    is Recurrence.EveryNDays -> EventRecurrence.EveryNDays(days)
    is Recurrence.EveryNMonths -> EventRecurrence.EveryNMonths(months, dayOfMonth)
}

/**
 * Splits the events into the engine's income and expense events.
 *
 * @throws IllegalArgumentException if an event breaks an engine rule
 */
fun List<BudgetEvent>.toEngineEvents(): Pair<List<IncomeEvent>, List<ExpenseEvent>> {
    val income = filter { it.kind == EventKind.INCOME }.map {
        IncomeEvent(
            id = it.id,
            amount = it.amount,
            recurrence = it.recurrence.toEngine(),
            startDate = it.startDate,
            endDate = it.endDate,
        )
    }
    val expenses = filter { it.kind != EventKind.INCOME }.map {
        ExpenseEvent(
            id = it.id,
            amount = it.amount,
            isMandatory = it.kind == EventKind.MANDATORY_EXPENSE,
            recurrence = it.recurrence.toEngine(),
            startDate = it.startDate,
            endDate = it.endDate,
        )
    }
    return income to expenses
}
