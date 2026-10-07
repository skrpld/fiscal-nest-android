/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.envelope

import io.github.skrpld.fiscalnest.domain.assertDecimal
import io.github.skrpld.fiscalnest.domain.date
import io.github.skrpld.fiscalnest.domain.dec
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopePolicy
import io.github.skrpld.fiscalnest.domain.model.EnvelopeRole
import io.github.skrpld.fiscalnest.domain.readmeData
import io.github.skrpld.fiscalnest.domain.model.deleteEnvelope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EnvelopeCalculatorTest {
    private val today = date(2026, 8, 7)

    private fun deposit(id: String, envelopeId: String, amount: String, day: Int = 1, month: Int = 8) =
        EnvelopeOperation(id, envelopeId, EnvelopeOperationType.DEPOSIT, dec(amount), date(2026, month, day))

    private fun withdrawal(id: String, envelopeId: String, amount: String, day: Int, month: Int = 8) =
        EnvelopeOperation(id, envelopeId, EnvelopeOperationType.WITHDRAWAL, dec(amount), date(2026, month, day))

    private fun data(envelope: Envelope, vararg operations: EnvelopeOperation) =
        AppData(envelopes = listOf(envelope), envelopeOperations = operations.toList())

    @Test
    fun `flexible envelopes give out the whole balance`() {
        val envelope = Envelope("a", "Gifts")
        val status = EnvelopeCalculator.status(
            data(envelope, deposit("1", "a", "1000"), withdrawal("2", "a", "300", 5), deposit("3", "other", "50")),
            "a",
            today,
        )!!
        assertDecimal("700", status.balance)
        assertDecimal("700", status.available)
        assertDecimal("300", status.withdrawnInPeriod)
        assertNull(status.lock)
    }

    @Test
    fun `period limits count withdrawals of the current period only`() {
        val envelope = Envelope("a", "Cafes", EnvelopePolicy.PeriodLimit(dec("500")))
        val operations = arrayOf(
            deposit("1", "a", "3000", month = 6),
            withdrawal("2", "a", "450", 20, month = 7),
            withdrawal("3", "a", "200", 3),
        )
        val status = EnvelopeCalculator.status(data(envelope, *operations), "a", today)!!
        assertDecimal("2350", status.balance)
        assertDecimal("200", status.withdrawnInPeriod)
        assertDecimal("300", status.available)
        assertNull(status.lock)

        val spent = EnvelopeCalculator.status(data(envelope, *operations, withdrawal("4", "a", "300", 6)), "a", today)!!
        assertDecimal("0", spent.available)
        assertEquals(EnvelopeLock.PeriodLimitReached, spent.lock)
    }

    @Test
    fun `period limits never exceed the balance`() {
        val envelope = Envelope("a", "Cafes", EnvelopePolicy.PeriodLimit(dec("500")))
        val status = EnvelopeCalculator.status(data(envelope, deposit("1", "a", "120")), "a", today)!!
        assertDecimal("120", status.available)
    }

    @Test
    fun `locked envelopes open on their date`() {
        val envelope = Envelope("a", "Deposit", EnvelopePolicy.LockedUntil(date(2026, 9, 1)))
        val data = data(envelope, deposit("1", "a", "10000"))

        val locked = EnvelopeCalculator.status(data, "a", today)!!
        assertDecimal("0", locked.available)
        assertEquals(EnvelopeLock.UntilDate(date(2026, 9, 1)), locked.lock)

        val open = EnvelopeCalculator.status(data, "a", date(2026, 9, 1))!!
        assertDecimal("10000", open.available)
        assertNull(open.lock)
    }

    @Test
    fun `goal envelopes open once the target is reached`() {
        val envelope = Envelope("a", "Vacation", EnvelopePolicy.UntilTarget, target = dec("50000"))
        val saving = EnvelopeCalculator.status(data(envelope, deposit("1", "a", "20000")), "a", today)!!
        assertDecimal("0", saving.available)
        assertEquals(EnvelopeLock.UntilTarget(dec("30000")), saving.lock)
        assertEquals(0.4f, saving.targetProgress!!, 0.0001f)

        val reached = EnvelopeCalculator.status(
            data(envelope, deposit("1", "a", "20000"), deposit("2", "a", "30000")),
            "a",
            today,
        )!!
        assertDecimal("50000", reached.available)
        assertNull(reached.lock)
        assertEquals(1f, reached.targetProgress!!, 0.0001f)
    }

    @Test
    fun `summarizes all envelopes`() {
        val data = AppData(
            envelopes = listOf(
                Envelope("a", "Gifts"),
                Envelope("b", "Deposit", EnvelopePolicy.LockedUntil(date(2027, 1, 1))),
            ),
            envelopeOperations = listOf(deposit("1", "a", "1000"), deposit("2", "b", "5000")),
        )
        val summary = EnvelopeCalculator.summary(data, today)
        assertDecimal("6000", summary.total)
        assertDecimal("1000", summary.available)
        assertEquals(listOf("a", "b"), summary.statuses.map { it.envelope.id })
    }

    @Test
    fun `cushion envelopes provide the cushion balance`() {
        assertNull(EnvelopeCalculator.cushionBalance(readmeData))
        assertDecimal("5000", readmeData.effectiveCushionCurrent())

        val data = readmeData.copy(
            envelopes = listOf(
                Envelope("card", "Card", role = EnvelopeRole.SPENDING),
                Envelope("deposit", "Deposit", role = EnvelopeRole.CUSHION),
                Envelope("cash", "Cash", role = EnvelopeRole.CUSHION),
            ),
            envelopeOperations = listOf(
                deposit("1", "card", "9000"),
                deposit("2", "deposit", "7000"),
                deposit("3", "cash", "1500"),
                withdrawal("4", "cash", "500", 3),
            ),
        )
        assertDecimal("8000", EnvelopeCalculator.cushionBalance(data)!!)
        assertDecimal("8000", data.effectiveCushionCurrent())

        val empty = readmeData.copy(envelopes = listOf(Envelope("deposit", "Deposit", role = EnvelopeRole.CUSHION)))
        assertDecimal("0", empty.effectiveCushionCurrent())
    }

    @Test
    fun `deleting an envelope removes its history`() {
        val data = AppData(
            envelopes = listOf(Envelope("a", "Gifts"), Envelope("b", "Car")),
            envelopeOperations = listOf(deposit("1", "a", "1000"), deposit("2", "b", "5000")),
        ).deleteEnvelope("a")
        assertEquals(listOf("b"), data.envelopes.map { it.id })
        assertEquals(listOf("2"), data.envelopeOperations.map { it.id })
        assertNull(EnvelopeCalculator.status(data, "a", today))
    }
}
