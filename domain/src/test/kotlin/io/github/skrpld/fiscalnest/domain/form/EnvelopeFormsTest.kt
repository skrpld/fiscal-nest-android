/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.form

import io.github.skrpld.fiscalnest.domain.assertDecimal
import io.github.skrpld.fiscalnest.domain.date
import io.github.skrpld.fiscalnest.domain.dec
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopePolicy
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import io.github.skrpld.fiscalnest.domain.readmeData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnvelopeFormsTest {
    private val today = date(2026, 8, 7)

    @Test
    fun `builds envelopes of every policy`() {
        val base = EnvelopeDraft.new(today).copy(name = "  Vacation ", initialBalance = "1 000")

        val flexible = (base.validate("e") as Validation.Valid).value
        assertEquals(Envelope("e", "Vacation", EnvelopePolicy.Flexible), flexible.envelope)
        assertDecimal("1000", flexible.initialBalance)

        val limited = (base.copy(policyType = EnvelopePolicyType.PERIOD_LIMIT, limit = "500").validate("e") as Validation.Valid)
        assertEquals(EnvelopePolicy.PeriodLimit(dec("500")), limited.value.envelope.policy)

        val locked = (base.copy(policyType = EnvelopePolicyType.LOCKED_UNTIL).validate("e") as Validation.Valid)
        assertEquals(EnvelopePolicy.LockedUntil(date(2027, 2, 7)), locked.value.envelope.policy)

        val goal = (base.copy(policyType = EnvelopePolicyType.UNTIL_TARGET, target = "50000").validate("e") as Validation.Valid)
        assertEquals(EnvelopePolicy.UntilTarget, goal.value.envelope.policy)
        assertDecimal("50000", goal.value.envelope.target!!)
    }

    @Test
    fun `reports invalid envelope fields`() {
        val draft = EnvelopeDraft.new(today).copy(
            policyType = EnvelopePolicyType.UNTIL_TARGET,
            initialBalance = "abc",
        )
        val errors = (draft.validate("e") as Validation.Invalid).errors
        assertEquals(
            mapOf(
                EnvelopeField.NAME to FieldError.REQUIRED,
                EnvelopeField.TARGET to FieldError.REQUIRED,
                EnvelopeField.INITIAL_BALANCE to FieldError.INVALID_NUMBER,
            ),
            errors,
        )
        val limit = EnvelopeDraft.new(today).copy(name = "Cafes", policyType = EnvelopePolicyType.PERIOD_LIMIT, limit = "0")
        assertEquals(mapOf(EnvelopeField.LIMIT to FieldError.MUST_BE_POSITIVE), (limit.validate("e") as Validation.Invalid).errors)
    }

    @Test
    fun `restores the editor state of an envelope`() {
        val envelope = Envelope("e", "Cafes", EnvelopePolicy.PeriodLimit(dec("500.5")), target = dec("2000"))
        val draft = EnvelopeDraft.from(envelope, today)
        assertEquals(EnvelopePolicyType.PERIOD_LIMIT, draft.policyType)
        assertEquals("500.5", draft.limit)
        assertEquals("2000", draft.target)
        assertEquals(envelope, (draft.validate("e") as Validation.Valid).value.envelope)
    }

    @Test
    fun `withdrawals cannot exceed what is available`() {
        val draft = EnvelopeOperationDraft(amount = "600", note = " Dinner ", date = today)
        val tooMuch = draft.validate("o", "e", EnvelopeOperationType.WITHDRAWAL, today, available = dec("500"))
        assertEquals(mapOf(EnvelopeOperationField.AMOUNT to FieldError.EXCEEDS_AVAILABLE), (tooMuch as Validation.Invalid).errors)

        val deposit = draft.validate("o", "e", EnvelopeOperationType.DEPOSIT, today, available = dec("0"))
        val operation = (deposit as Validation.Valid).value
        assertEquals("Dinner", operation.note)
        assertDecimal("600", operation.amount)

        val future = draft.copy(date = today.plusDays(1))
            .validate("o", "e", EnvelopeOperationType.DEPOSIT, today, available = dec("0"))
        assertEquals(mapOf(EnvelopeOperationField.DATE to FieldError.IN_THE_FUTURE), (future as Validation.Invalid).errors)
    }

    @Test
    fun `quick setup fields are optional but income needs a pay day`() {
        assertTrue(QuickSetupDraft().validate() is Validation.Valid)
        val errors = (QuickSetupDraft(income = "50000", cushionTarget = "x").validate() as Validation.Invalid).errors
        assertEquals(
            mapOf(QuickSetupField.PAY_DAY to FieldError.REQUIRED, QuickSetupField.CUSHION_TARGET to FieldError.INVALID_NUMBER),
            errors,
        )
        val outOfRange = (QuickSetupDraft(payDay = "32").validate() as Validation.Invalid).errors
        assertEquals(mapOf(QuickSetupField.PAY_DAY to FieldError.OUT_OF_RANGE), outOfRange)
    }

    @Test
    fun `quick setup adds the income and aligns the period with the pay day`() {
        val values = (QuickSetupDraft("80000", "10", "5000", "30000").validate() as Validation.Valid).value
        val data = AppData(onboardingCompleted = false).applyQuickSetup(values, "salary", "Salary", today)

        val income = data.events.single()
        assertEquals(EventKind.INCOME, income.kind)
        assertEquals(Recurrence.EveryNMonths(1, 10), income.recurrence)
        assertEquals(date(2026, 7, 10), income.startDate)
        assertDecimal("80000", income.amount)
        assertEquals(PeriodRule.Monthly(10), data.settings.period)
        assertDecimal("5000", data.settings.cushionCurrent)
        assertDecimal("30000", data.settings.cushionTarget)
        assertTrue(data.onboardingCompleted)
    }

    @Test
    fun `an empty quick setup only finishes the guide`() {
        val values = (QuickSetupDraft().validate() as Validation.Valid).value
        val start = readmeData.copy(onboardingCompleted = false)
        val data = start.applyQuickSetup(values, "salary", "Salary", today)
        assertEquals(start.events, data.events)
        assertEquals(start.settings.period, data.settings.period)
        assertTrue(data.onboardingCompleted)
        assertFalse(start.onboardingCompleted)
    }

    @Test
    fun `finds the latest pay date`() {
        assertEquals(date(2026, 8, 7), latestPayDate(today, 7))
        assertEquals(date(2026, 8, 1), latestPayDate(today, 1))
        assertEquals(date(2026, 7, 31), latestPayDate(today, 31))
        assertEquals(date(2026, 2, 28), latestPayDate(date(2026, 3, 5), 30))
        assertEquals(PeriodRule.Monthly(28), AppData().applyQuickSetup(QuickSetupValues(null, 30, dec("0"), dec("0")), "i", "n", today).settings.period)
    }
}
