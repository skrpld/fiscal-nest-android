/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.queue

import io.github.skrpld.fiscalnest.domain.date
import io.github.skrpld.fiscalnest.domain.dec
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.AppDataValidator
import io.github.skrpld.fiscalnest.domain.model.BudgetEvent
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopeRole
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.OccurrenceStatus
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import io.github.skrpld.fiscalnest.domain.model.confirmOccurrence
import io.github.skrpld.fiscalnest.domain.model.deleteEnvelope
import io.github.skrpld.fiscalnest.domain.model.deleteEnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.deleteEvent
import io.github.skrpld.fiscalnest.domain.model.reopenOccurrence
import io.github.skrpld.fiscalnest.domain.model.skipOccurrence
import io.github.skrpld.fiscalnest.domain.model.startQueue
import io.github.skrpld.fiscalnest.domain.model.upsertEnvelopeOperation
import io.github.skrpld.fiscalnest.domain.rent
import io.github.skrpld.fiscalnest.domain.salary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ConfirmationQueueTest {
    private val today = date(2026, 8, 10)
    private val card = Envelope(id = "card", name = "Card", role = EnvelopeRole.SPENDING)
    private val data = AppData(
        events = listOf(salary.copy(envelopeId = "card"), rent.copy(envelopeId = "card")),
        envelopes = listOf(card),
        settings = AppData().settings.copy(period = PeriodRule.Monthly(startDay = 1)),
        queueStart = date(2026, 8, 1),
    )

    private fun AppData.balance(): BigDecimal = EnvelopeCalculator.status(this, "card", today)!!.balance

    @Test
    fun `due occurrences since the queue start wait for confirmation, oldest first`() {
        val pending = ConfirmationQueue.pending(data, today)
        assertEquals(listOf("salary" to date(2026, 8, 1), "rent" to date(2026, 8, 5)), pending.map { it.event.id to it.date })
    }

    @Test
    fun `nothing older than the queue start and nothing in the future`() {
        assertTrue(ConfirmationQueue.pending(data, date(2026, 7, 31)).isEmpty())
        assertEquals(1, ConfirmationQueue.pending(data, date(2026, 8, 4)).size)
        val later = ConfirmationQueue.pending(data, date(2026, 9, 6))
        assertEquals(4, later.size)
    }

    @Test
    fun `the queue starts once, at the start of the current period`() {
        val started = AppData().startQueue(date(2026, 8, 1))
        assertEquals(date(2026, 8, 1), started.queueStart)
        assertEquals(date(2026, 8, 1), started.startQueue(date(2026, 9, 1)).queueStart)
        val notStarted = data.copy(queueStart = null)
        assertEquals(2, ConfirmationQueue.pending(notStarted, today).size)
    }

    @Test
    fun `confirming moves the money and leaves the queue`() {
        val salaryEvent = data.events.first { it.id == "salary" }
        val received = data.confirmOccurrence(salaryEvent, date(2026, 8, 1), "card", dec("51000"))
        assertEquals(0, dec("51000").compareTo(received.balance()))
        val deposit = received.envelopeOperations.single()
        assertEquals(EnvelopeOperationType.DEPOSIT, deposit.type)
        assertEquals("Salary", deposit.note)
        assertEquals(listOf("rent"), ConfirmationQueue.pending(received, today).map { it.event.id })

        val rentEvent = data.events.first { it.id == "rent" }
        val paid = received.confirmOccurrence(rentEvent, date(2026, 8, 5), "card", dec("20000"))
        assertEquals(0, dec("31000").compareTo(paid.balance()))
        assertTrue(ConfirmationQueue.pending(paid, today).isEmpty())
        assertEquals(emptyList<String>(), AppDataValidator.problems(paid))

        val again = paid.confirmOccurrence(rentEvent, date(2026, 8, 5), "card", dec("21000"))
        assertEquals(2, again.envelopeOperations.size)
        assertEquals(2, again.handledOccurrences.size)
        assertEquals(0, dec("30000").compareTo(again.balance()))
    }

    @Test
    fun `skipping moves nothing and reopening brings the occurrence back`() {
        val skipped = data.skipOccurrence("rent", date(2026, 8, 5))
        assertEquals(OccurrenceStatus.SKIPPED, skipped.handledOccurrences.single().status)
        assertTrue(skipped.envelopeOperations.isEmpty())
        assertEquals(1, ConfirmationQueue.pending(skipped, today).size)

        val salaryEvent = data.events.first { it.id == "salary" }
        val confirmed = skipped.confirmOccurrence(salaryEvent, date(2026, 8, 1), "card", dec("50000"))
        val reopened = confirmed.reopenOccurrence("salary", date(2026, 8, 1))
        assertTrue(reopened.envelopeOperations.isEmpty())
        assertEquals(listOf("salary"), ConfirmationQueue.pending(reopened, today).map { it.event.id })
    }

    @Test
    fun `deleting the operation of a confirmed occurrence puts it back, undo restores it`() {
        val salaryEvent = data.events.first { it.id == "salary" }
        val confirmed = data.confirmOccurrence(salaryEvent, date(2026, 8, 1), "card", dec("50000"))
        val operation = confirmed.envelopeOperations.single()
        val deleted = confirmed.deleteEnvelopeOperation(operation.id)
        assertTrue(deleted.handledOccurrences.isEmpty())
        assertEquals(2, ConfirmationQueue.pending(deleted, today).size)
        assertEquals(confirmed, deleted.upsertEnvelopeOperation(operation))
    }

    @Test
    fun `deleting an event or an envelope keeps the rest consistent`() {
        val salaryEvent = data.events.first { it.id == "salary" }
        val confirmed = data.confirmOccurrence(salaryEvent, date(2026, 8, 1), "card", dec("50000"))
        val withoutEvent = confirmed.deleteEvent("salary")
        assertTrue(withoutEvent.handledOccurrences.isEmpty())
        assertEquals(1, withoutEvent.envelopeOperations.size)
        assertEquals(emptyList<String>(), AppDataValidator.problems(withoutEvent))

        val withoutEnvelope = confirmed.deleteEnvelope("card")
        assertTrue(withoutEnvelope.events.all { it.envelopeId == null })
        assertEquals(emptyList<String>(), AppDataValidator.problems(withoutEnvelope))
    }

    @Test
    fun `the validator rejects unknown envelopes and duplicate answers`() {
        val broken = data.copy(
            events = listOf(rent.copy(envelopeId = "missing")),
            handledOccurrences = data.skipOccurrence("rent", date(2026, 8, 5)).handledOccurrences.let { it + it },
        )
        val problems = AppDataValidator.problems(broken)
        assertTrue("Event paid through an unknown envelope: rent" in problems)
        assertTrue("Duplicate answers for an event occurrence" in problems)
    }

    @Test
    fun `monthly occurrences match the engine rules`() {
        val endOfMonth = event(Recurrence.EveryNMonths(months = 1, dayOfMonth = 31), start = date(2026, 1, 15))
        assertEquals(
            listOf(date(2026, 1, 31), date(2026, 2, 28), date(2026, 3, 31), date(2026, 4, 30)),
            endOfMonth.occurrencesBetween(date(2025, 12, 1), date(2026, 4, 30)),
        )
        val quarterly = event(Recurrence.EveryNMonths(months = 3, dayOfMonth = 10), start = date(2026, 1, 11))
        // January 10 is before the start, so the first occurrence is in February.
        assertEquals(
            listOf(date(2026, 2, 10), date(2026, 5, 10), date(2026, 8, 10)),
            quarterly.occurrencesBetween(date(2026, 1, 1), date(2026, 9, 30)),
        )
        assertEquals(listOf(date(2026, 5, 10)), quarterly.occurrencesBetween(date(2026, 2, 11), date(2026, 8, 9)))
    }

    @Test
    fun `daily, one-time and finished events`() {
        val everyThird = event(Recurrence.EveryNDays(3), start = date(2026, 1, 1), end = date(2026, 1, 12))
        assertEquals(listOf(date(2026, 1, 7), date(2026, 1, 10)), everyThird.occurrencesBetween(date(2026, 1, 5), date(2026, 1, 31)))
        val once = event(Recurrence.Once, start = date(2026, 3, 3))
        assertEquals(listOf(date(2026, 3, 3)), once.occurrencesBetween(date(2026, 3, 1), date(2026, 3, 31)))
        assertTrue(once.occurrencesBetween(date(2026, 3, 4), date(2026, 3, 31)).isEmpty())
        assertTrue(everyThird.occurrencesBetween(date(2026, 2, 1), date(2026, 1, 1)).isEmpty())
        assertNull(everyThird.occurrencesBetween(date(2027, 1, 1), date(2027, 2, 1)).firstOrNull())
    }

    private fun event(recurrence: Recurrence, start: java.time.LocalDate, end: java.time.LocalDate? = null) = BudgetEvent(
        id = "e",
        name = "E",
        kind = EventKind.OPTIONAL_EXPENSE,
        amount = dec("1"),
        recurrence = recurrence,
        startDate = start,
        endDate = end,
    )
}
