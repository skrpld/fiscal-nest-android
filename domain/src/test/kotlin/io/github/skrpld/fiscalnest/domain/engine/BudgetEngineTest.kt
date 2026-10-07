/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.engine

import fiscalnest.core.CashReserve
import fiscalnest.core.PiggyBankMode
import io.github.skrpld.fiscalnest.domain.assertDecimal
import io.github.skrpld.fiscalnest.domain.date
import io.github.skrpld.fiscalnest.domain.dec
import io.github.skrpld.fiscalnest.domain.model.BudgetSettings
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopeRole
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.updateSettings
import io.github.skrpld.fiscalnest.domain.readmeData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BudgetEngineTest {
    @Test
    fun `starts the cushion from the cushion envelopes`() {
        val data = readmeData.copy(
            envelopes = listOf(Envelope("deposit", "Deposit", role = EnvelopeRole.CUSHION)),
            envelopeOperations = listOf(
                EnvelopeOperation("1", "deposit", EnvelopeOperationType.DEPOSIT, dec("10000"), date(2026, 7, 1)),
            ),
        )
        val outcome = assertInstanceOf(ForecastOutcome.Success::class.java, BudgetEngine.forecast(data, date(2026, 8, 7)))

        assertDecimal("0.5", outcome.current.distribution.cushionFillPct)
    }

    @Test
    fun `reproduces the engine README forecast from app data`() {
        val outcome = assertInstanceOf(ForecastOutcome.Success::class.java, BudgetEngine.forecast(readmeData, date(2026, 8, 7)))

        assertEquals(date(2026, 8, 1), outcome.period.start)
        assertEquals(date(2026, 8, 31), outcome.period.endInclusive)
        assertDecimal("3500", outcome.alreadySpent)
        assertEquals(3, outcome.periods.size)

        val august = outcome.current
        assertDecimal("21000", august.distribution.freeRemainder)
        assertDecimal("20000", august.cashFlow.paidMandatory)
        assertDecimal("4000", august.cashFlow.mustReserve)
        assertDecimal("22500", august.cashFlow.available)
        assertDecimal("17500", august.closingBalance)
        assertDecimal("700", august.dailyMetrics.dailyActual)
        assertDecimal("900", august.dailyMetrics.dailyCashflow)

        val september = outcome.periods[1]
        assertEquals(date(2026, 9, 1), september.periodStart)
        assertDecimal("17500", september.openingBalance)
        assertDecimal("39500", september.freeBalance)
    }

    @Test
    fun `counts only spending of the current period up to today`() {
        val period = io.github.skrpld.fiscalnest.domain.period.DateRange(date(2026, 8, 1), date(2026, 8, 31))
        assertDecimal("3500", BudgetEngine.alreadySpent(readmeData.spendings, period, date(2026, 8, 7)))
        assertDecimal("2000", BudgetEngine.alreadySpent(readmeData.spendings, period, date(2026, 8, 5)))
    }

    @Test
    fun `follows a pay-day period rule`() {
        val data = readmeData.updateSettings { it.copy(period = PeriodRule.Monthly(startDay = 5)) }
        val outcome = assertInstanceOf(ForecastOutcome.Success::class.java, BudgetEngine.forecast(data, date(2026, 8, 3)))

        assertEquals(date(2026, 7, 5), outcome.period.start)
        assertEquals(date(2026, 8, 4), outcome.period.endInclusive)
        assertEquals(date(2026, 8, 5), outcome.periods[1].periodStart)
    }

    @Test
    fun `reports engine validation failures instead of throwing`() {
        val data = readmeData.updateSettings { it.copy(cushionLevels = emptyList()) }
        val outcome = assertInstanceOf(ForecastOutcome.Failure::class.java, BudgetEngine.forecast(data, date(2026, 8, 7)))
        assertTrue(outcome.message.startsWith("Criticality levels must be non-empty"))
    }

    @Test
    fun `calculates the engine README what-if example`() {
        val settings = BudgetSettings(
            piggyBankMode = PiggyBankMode.FIXED_AMOUNT,
            piggyBankTarget = dec("5000"),
            piggyBankAdmissibility = dec("0.80"),
            cashReserves = setOf(CashReserve.CUSHION_TOPUP),
        )
        val outcome = BudgetEngine.whatIf(
            WhatIfValues(dec("50000"), dec("20000"), dec("10000"), dec("5000"), dec("20000")),
            settings,
        )
        val result = assertInstanceOf(WhatIfOutcome.Success::class.java, outcome).result

        assertFalse(result.expenseCrisis)
        assertTrue(result.cushionCrisis)
        assertEquals("Critical", result.activeCriticalityLevel)
        assertDecimal("4000", result.cushionTopup)
        assertDecimal("5000", result.piggyBankActual)
        assertDecimal("11000", result.freeRemainder)
    }

    @Test
    fun `sorts cushion levels for the engine`() {
        val settings = BudgetSettings().let { it.copy(cushionLevels = it.cushionLevels.reversed()) }
        val config = settings.toEngineConfig()
        assertEquals(listOf("Critical", "Warning"), config.criticalityLevels.map { it.name })
    }
}
