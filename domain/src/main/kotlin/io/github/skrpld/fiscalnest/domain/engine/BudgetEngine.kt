/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.engine

import fiscalnest.core.BudgetCalculator
import fiscalnest.core.CushionState
import fiscalnest.core.DistributionResult
import fiscalnest.core.ForecastInput
import fiscalnest.core.ForecastResult
import fiscalnest.core.WhatIfInput
import io.github.skrpld.fiscalnest.domain.envelope.effectiveCushionCurrent
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.BudgetSettings
import io.github.skrpld.fiscalnest.domain.model.Spending
import io.github.skrpld.fiscalnest.domain.period.DateRange
import io.github.skrpld.fiscalnest.domain.period.PeriodResolver
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Result of a forecast for the stored budget.
 */
sealed interface ForecastOutcome {
    /**
     * @property period the current period, the first one of [periods]
     * @property today date the cash view is taken at
     * @property alreadySpent unscheduled spending logged in the current period up to [today]
     * @property periods one result per projected period, the current one first
     */
    data class Success(
        val period: DateRange,
        val today: LocalDate,
        val alreadySpent: BigDecimal,
        val periods: List<ForecastResult>,
    ) : ForecastOutcome {
        val current: ForecastResult get() = periods.first()
    }

    /** The engine rejected the input; [message] is the engine's validation message. */
    data class Failure(val message: String) : ForecastOutcome
}

/**
 * Aggregate amounts of a what-if calculation.
 */
data class WhatIfValues(
    val income: BigDecimal,
    val mandatory: BigDecimal,
    val optional: BigDecimal,
    val cushionCurrent: BigDecimal,
    val cushionTarget: BigDecimal,
)

/**
 * Result of a what-if calculation.
 */
sealed interface WhatIfOutcome {
    data class Success(val result: DistributionResult) : WhatIfOutcome

    data class Failure(val message: String) : WhatIfOutcome
}

/**
 * Runs the engine on the stored budget. Stateless and safe to call from any thread.
 */
object BudgetEngine {
    /**
     * Projects the budget from the period containing [today].
     */
    fun forecast(data: AppData, today: LocalDate): ForecastOutcome {
        val settings = data.settings
        val period = PeriodResolver.periodContaining(settings.period, today)
        val alreadySpent = alreadySpent(data.spendings, period, today)
        return try {
            val (income, expenses) = data.events.toEngineEvents()
            val results = BudgetCalculator.calculateForecast(
                ForecastInput(
                    incomeEvents = income,
                    expenseEvents = expenses,
                    periodStart = period.start,
                    periodEnd = period.endInclusive,
                    currentDate = today,
                    alreadySpent = alreadySpent,
                    forecastPeriods = settings.forecastPeriods,
                    config = settings.toEngineConfig(),
                    cushionState = CushionState(data.effectiveCushionCurrent(), settings.cushionTarget),
                ),
            )
            ForecastOutcome.Success(period, today, alreadySpent, results)
        } catch (e: IllegalArgumentException) {
            ForecastOutcome.Failure(e.message.orEmpty())
        }
    }

    /**
     * Distributes aggregate amounts with the stored budget rules.
     */
    fun whatIf(values: WhatIfValues, settings: BudgetSettings): WhatIfOutcome = try {
        val result = BudgetCalculator.calculateWhatIf(
            WhatIfInput(
                income = values.income,
                mandatory = values.mandatory,
                optional = values.optional,
                cushionState = CushionState(values.cushionCurrent, values.cushionTarget),
                config = settings.toEngineConfig(),
            ),
        )
        WhatIfOutcome.Success(result)
    } catch (e: IllegalArgumentException) {
        WhatIfOutcome.Failure(e.message.orEmpty())
    }

    /**
     * Sums the spending dated inside [period] on or before [today].
     */
    fun alreadySpent(spendings: List<Spending>, period: DateRange, today: LocalDate): BigDecimal = spendings
        .filter { it.date in period && it.date <= today }
        .fold(BigDecimal.ZERO) { total, spending -> total + spending.amount }
}
