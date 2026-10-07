/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.model

import io.github.skrpld.fiscalnest.domain.date
import io.github.skrpld.fiscalnest.domain.dec
import io.github.skrpld.fiscalnest.domain.readmeData
import io.github.skrpld.fiscalnest.domain.rent
import io.github.skrpld.fiscalnest.domain.salary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppDataOperationsTest {
    @Test
    fun `upsert replaces in place and appends new events`() {
        val renamed = rent.copy(name = "Flat")
        val data = readmeData.upsertEvent(renamed)
        assertEquals(listOf(salary, renamed), data.events)

        val extra = rent.copy(id = "extra")
        assertEquals(listOf(salary, renamed, extra), data.upsertEvent(extra).events)
    }

    @Test
    fun `deletes events and spending by id`() {
        assertEquals(listOf(salary), readmeData.deleteEvent("rent").events)
        assertEquals(3, readmeData.deleteSpending("s1").spendings.size)
        val added = readmeData.upsertSpending(Spending("new", dec("1"), date(2026, 8, 8)))
        assertEquals("new", added.spendings.last().id)
    }

    @Test
    fun `spending from an envelope is mirrored by a withdrawal`() {
        val card = Envelope(id = "card", name = "Card", role = EnvelopeRole.SPENDING)
        val base = AppData().upsertEnvelope(card)
        val spending = Spending("s", dec("40"), date(2026, 8, 8), "Lunch", envelopeId = "card")

        val spent = base.upsertSpending(spending)
        val operation = spent.envelopeOperations.single()
        assertEquals(EnvelopeOperationType.WITHDRAWAL, operation.type)
        assertEquals(dec("40"), operation.amount)
        assertEquals("card", operation.envelopeId)

        val edited = spent.upsertSpending(spending.copy(amount = dec("50")))
        assertEquals(dec("50"), edited.envelopeOperations.single().amount)

        assertTrue(edited.deleteSpending("s").envelopeOperations.isEmpty())
        assertTrue(edited.deleteEnvelope("card").spendings.single().envelopeId == null)

        val mirror = edited.envelopeOperations.single()
        assertEquals(OperationSource.FromSpending("s"), mirror.source)
        val withoutMirror = edited.deleteEnvelopeOperation(mirror.id)
        assertTrue(withoutMirror.spendings.isEmpty())
        assertEquals(edited, withoutMirror.upsertEnvelopeOperation(mirror))
    }

    @Test
    fun `sorts cushion levels by threshold`() {
        val levels = BudgetSettings.defaultCushionLevels().reversed()
        val sorted = BudgetSettings().withCushionLevels(levels).cushionLevels
        assertEquals(listOf("Critical", "Warning"), sorted.map { it.name })
    }

    @Test
    fun `knows when an event has ended`() {
        val today = date(2026, 10, 2)
        assertFalse(rent.hasEndedBefore(today))
        assertTrue(rent.copy(endDate = date(2026, 10, 1)).hasEndedBefore(today))
        assertTrue(rent.copy(recurrence = Recurrence.Once, startDate = date(2026, 9, 1)).hasEndedBefore(today))
        assertFalse(rent.copy(recurrence = Recurrence.Once, startDate = today).hasEndedBefore(today))
    }

    @Test
    fun `validator accepts defaults and the sample data`() {
        assertEquals(emptyList<String>(), AppDataValidator.problems(AppData()))
        assertEquals(emptyList<String>(), AppDataValidator.problems(readmeData))
    }

    @Test
    fun `validator rejects duplicate level thresholds`() {
        val levels = BudgetSettings.defaultCushionLevels()
        val duplicate = levels + levels.first().copy(name = "Copy", maxFillRatio = dec("0.3"))
        val data = AppData().updateSettings { it.copy(cushionLevels = duplicate) }
        assertTrue("Duplicate cushion level thresholds" in AppDataValidator.problems(data))
    }
}
