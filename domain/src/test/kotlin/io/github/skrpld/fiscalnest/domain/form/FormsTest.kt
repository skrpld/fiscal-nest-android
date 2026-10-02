/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.form

import fiscalnest.core.PiggyBankMode
import fiscalnest.core.TopupMode
import io.github.skrpld.fiscalnest.domain.assertDecimal
import io.github.skrpld.fiscalnest.domain.date
import io.github.skrpld.fiscalnest.domain.model.BudgetSettings
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import io.github.skrpld.fiscalnest.domain.rent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FormsTest {
    private val today = date(2026, 10, 2)

    @Test
    fun `new event draft reports missing fields`() {
        val result = EventDraft.new(today).validate("id")
        val invalid = assertInstanceOf(Validation.Invalid::class.java, result)
        assertEquals(mapOf(EventField.NAME to FieldError.REQUIRED, EventField.AMOUNT to FieldError.REQUIRED), invalid.errors)
    }

    @Test
    fun `builds a monthly event`() {
        val draft = EventDraft.new(today, EventKind.INCOME).copy(name = "  Salary ", amount = "50 000", dayOfMonth = "31")
        val event = assertInstanceOf(Validation.Valid::class.java, draft.validate("salary")).value
        assertEquals("Salary", (event as io.github.skrpld.fiscalnest.domain.model.BudgetEvent).name)
        assertEquals(EventKind.INCOME, event.kind)
        assertDecimal("50000", event.amount)
        assertEquals(Recurrence.EveryNMonths(1, 31), event.recurrence)
        assertEquals(today, event.startDate)
        assertNull(event.endDate)
    }

    @Test
    fun `validates recurrence fields and dates`() {
        val draft = EventDraft.new(today).copy(
            name = "Gym",
            amount = "0",
            recurrenceType = RecurrenceType.DAYS,
            interval = "0",
            endDate = today.minusDays(1),
        )
        val invalid = assertInstanceOf(Validation.Invalid::class.java, draft.validate("gym"))
        assertEquals(
            mapOf(
                EventField.AMOUNT to FieldError.MUST_BE_POSITIVE,
                EventField.INTERVAL to FieldError.OUT_OF_RANGE,
                EventField.END_DATE to FieldError.END_BEFORE_START,
            ),
            invalid.errors,
        )
    }

    @Test
    fun `one-time events drop the end date`() {
        val draft = EventDraft.new(today).copy(
            name = "Ticket",
            amount = "1200",
            recurrenceType = RecurrenceType.ONCE,
            interval = "x",
            endDate = today.minusDays(5),
        )
        val event = assertInstanceOf(Validation.Valid::class.java, draft.validate("t")).value
        assertEquals(
            io.github.skrpld.fiscalnest.domain.model.BudgetEvent(
                "t",
                "Ticket",
                EventKind.MANDATORY_EXPENSE,
                java.math.BigDecimal("1200"),
                Recurrence.Once,
                today,
                null,
            ),
            event,
        )
    }

    @Test
    fun `event draft round-trips an existing event`() {
        assertEquals(rent, (EventDraft.from(rent).validate(rent.id) as Validation.Valid).value)
    }

    @Test
    fun `spending cannot be in the future`() {
        val draft = SpendingDraft(amount = "10", date = today.plusDays(1))
        val invalid = assertInstanceOf(Validation.Invalid::class.java, draft.validate("s", today))
        assertEquals(mapOf(SpendingField.DATE to FieldError.IN_THE_FUTURE), invalid.errors)
    }

    @Test
    fun `cushion level thresholds must be unique`() {
        val levels = BudgetSettings.defaultCushionLevels()
        val draft = CushionLevelDraft("Mine", "30.0", TopupMode.PERCENT_OF_REMAINDER, "10", "50")
        val invalid = assertInstanceOf(Validation.Invalid::class.java, draft.validate(levels))
        assertEquals(mapOf(CushionLevelField.MAX_FILL to FieldError.DUPLICATE), invalid.errors)

        val level = (draft.copy(maxFillPercent = "50").validate(levels) as Validation.Valid).value
        assertDecimal("0.5", level.maxFillRatio)
        assertEquals(CushionLevelDraft.from(level).validate(levels), Validation.Valid(level))
    }

    @Test
    fun `cushion level percentages stay within range`() {
        val invalid = CushionLevelDraft("L", "0", TopupMode.PERCENT_OF_TARGET, "150", "").validate(emptyList())
        assertEquals(
            mapOf(
                CushionLevelField.MAX_FILL to FieldError.MUST_BE_POSITIVE,
                CushionLevelField.TOPUP to FieldError.OUT_OF_RANGE,
                CushionLevelField.ADMISSIBILITY to FieldError.REQUIRED,
            ),
            (invalid as Validation.Invalid).errors,
        )
    }

    @Test
    fun `what-if treats blank fields as zero`() {
        val values = (WhatIfDraft(income = "100").validate() as Validation.Valid).value
        assertDecimal("100", values.income)
        assertDecimal("0", values.cushionTarget)
        val invalid = WhatIfDraft(mandatory = "abc").validate() as Validation.Invalid
        assertEquals(mapOf(WhatIfField.MANDATORY to FieldError.INVALID_NUMBER), invalid.errors)
    }

    @Test
    fun `what-if needs an income or expense amount`() {
        val cushionOnly = WhatIfDraft(cushionCurrent = "5000", cushionTarget = "20000")
        assertEquals(false, cushionOnly.isEmpty)
        assertEquals(false, cushionOnly.hasAmounts)
        assertEquals(true, cushionOnly.copy(optional = "1").hasAmounts)
        assertEquals(true, WhatIfDraft().isEmpty)
    }

    @Test
    fun `piggy bank target depends on the mode`() {
        val percent = (PiggyBankDraft(PiggyBankMode.PERCENT_OF_REMAINDER, "10", "100").validate() as Validation.Valid).value
        assertDecimal("0.1", percent.target)
        val fixed = (PiggyBankDraft(PiggyBankMode.FIXED_AMOUNT, "5000", "80").validate() as Validation.Valid).value
        assertDecimal("5000", fixed.target)
        assertDecimal("0.8", fixed.admissibility)
        val invalid = PiggyBankDraft(PiggyBankMode.PERCENT_OF_REMAINDER, "500", "80").validate() as Validation.Invalid
        assertEquals(mapOf(PiggyBankField.TARGET to FieldError.OUT_OF_RANGE), invalid.errors)
    }

    @Test
    fun `period draft builds both rules`() {
        val monthly = PeriodDraft.from(PeriodRule.Monthly(5), today)
        assertEquals(PeriodRule.Monthly(5), (monthly.validate() as Validation.Valid).value)
        val fixed = monthly.copy(type = PeriodType.FIXED_LENGTH, lengthDays = "14")
        assertEquals(PeriodRule.FixedLength(14, today), (fixed.validate() as Validation.Valid).value)
        val invalid = monthly.copy(startDay = "29").validate() as Validation.Invalid
        assertEquals(mapOf(PeriodField.START_DAY to FieldError.OUT_OF_RANGE), invalid.errors)
    }

    @Test
    fun `cushion draft parses balances`() {
        val values = (CushionDraft.from(java.math.BigDecimal("5000.00"), java.math.BigDecimal.ZERO).validate() as Validation.Valid).value
        assertDecimal("5000", values.current)
        assertDecimal("0", values.target)
    }
}
