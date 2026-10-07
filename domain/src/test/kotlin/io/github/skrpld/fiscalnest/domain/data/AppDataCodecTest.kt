/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.data

import fiscalnest.core.CashReserve
import io.github.skrpld.fiscalnest.domain.date
import io.github.skrpld.fiscalnest.domain.dec
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.AppearanceSettings
import io.github.skrpld.fiscalnest.domain.model.BudgetEvent
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopePolicy
import io.github.skrpld.fiscalnest.domain.model.EnvelopeRole
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import io.github.skrpld.fiscalnest.domain.model.ThemeMode
import io.github.skrpld.fiscalnest.domain.model.updateSettings
import io.github.skrpld.fiscalnest.domain.readmeData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppDataCodecTest {
    private val fullData = readmeData
        .copy(
            events = readmeData.events + listOf(
                BudgetEvent("once", "Bonus", EventKind.INCOME, dec("1000.50"), Recurrence.Once, date(2026, 8, 15)),
                BudgetEvent(
                    "gym",
                    "Gym",
                    EventKind.OPTIONAL_EXPENSE,
                    dec("700"),
                    Recurrence.EveryNDays(14),
                    date(2026, 8, 2),
                    date(2026, 12, 31),
                ),
            ),
            appearance = AppearanceSettings(themeMode = ThemeMode.DARK, dynamicColor = false),
            envelopes = listOf(
                Envelope("cafes", "Cafes", EnvelopePolicy.PeriodLimit(dec("5000"))),
                Envelope("deposit", "Deposit", EnvelopePolicy.LockedUntil(date(2027, 1, 1))),
                Envelope("vacation", "Vacation", EnvelopePolicy.UntilTarget, target = dec("100000")),
                Envelope("gifts", "Gifts", role = EnvelopeRole.PIGGY_BANK),
            ),
            envelopeOperations = listOf(
                EnvelopeOperation("op1", "cafes", EnvelopeOperationType.DEPOSIT, dec("10000"), date(2026, 8, 1), "Start"),
                EnvelopeOperation("op2", "cafes", EnvelopeOperationType.WITHDRAWAL, dec("1200.50"), date(2026, 8, 3)),
            ),
            onboardingCompleted = false,
        )
        .updateSettings {
            it.copy(
                period = PeriodRule.FixedLength(14, date(2026, 8, 1)),
                cashReserves = setOf(CashReserve.PIGGY_BANK, CashReserve.UPCOMING_OPTIONAL),
                currencyCode = "EUR",
            )
        }

    @Test
    fun `round-trips every field`() {
        assertEquals(fullData, AppDataCodec.decode(AppDataCodec.encode(fullData)))
        assertEquals(fullData, AppDataCodec.decode(AppDataCodec.encode(fullData, pretty = true)))
    }

    @Test
    fun `keeps amounts exact`() {
        val json = AppDataCodec.encode(fullData)
        assertTrue("\"1000.50\"" in json, json)
        assertTrue("\"type\":\"every_n_days\"" in json, json)
        assertTrue("\"PIGGY_BANK\"" in json, json)
    }

    @Test
    fun `reads documents with missing and unknown keys`() {
        val data = AppDataCodec.decode("""{"events":[],"futureField":42,"settings":{"forecastPeriods":3}}""")
        assertEquals(3, data.settings.forecastPeriods)
        assertEquals(AppData().appearance, data.appearance)
        assertEquals(AppData.SCHEMA_VERSION, data.schemaVersion)
        assertTrue(data.envelopes.isEmpty())
        assertTrue(data.onboardingCompleted, "data saved before the guide existed must not show it")
    }

    @Test
    fun `accepts a valid backup`() {
        val result = AppDataCodec.decodeBackup(AppDataCodec.encode(fullData, pretty = true))
        assertEquals(fullData, result.getOrThrow())
    }

    @Test
    fun `rejects malformed and invalid backups`() {
        assertTrue(AppDataCodec.decodeBackup("not json").isFailure)
        assertTrue(AppDataCodec.decodeBackup("""{"events":[{"id":"x"}]}""").isFailure)
        assertTrue(AppDataCodec.decodeBackup("""{"settings":{"forecastPeriods":0}}""").isFailure)
        assertTrue(AppDataCodec.decodeBackup("""{"schemaVersion":99}""").isFailure)
        assertTrue(
            AppDataCodec.decodeBackup(
                """{"envelopeOperations":[{"id":"o","envelopeId":"missing","type":"DEPOSIT",""" +
                    """"amount":"1","date":"2026-01-01"}]}""",
            ).isFailure,
        )
        assertTrue(
            AppDataCodec.decodeBackup("""{"envelopes":[{"id":"e","name":"n","policy":{"type":"until_target"}}]}""").isFailure,
        )
        assertTrue(
            AppDataCodec.decodeBackup(
                """{"events":[{"id":"x","name":"n","kind":"INCOME","amount":"1E+999999999",""" +
                    """"recurrence":{"type":"once"},"startDate":"2026-01-01"}]}""",
            ).isFailure,
        )
    }

    @Test
    fun `in-memory repository applies updates`() = runTest {
        val repository = InMemoryBudgetRepository()
        repository.update { it.updateSettings { settings -> settings.copy(forecastPeriods = 12) } }
        assertEquals(12, repository.data.first().settings.forecastPeriods)
    }
}
