/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.period

import io.github.skrpld.fiscalnest.domain.date
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PeriodResolverTest {
    @Test
    fun `calendar month on the first`() {
        val range = PeriodResolver.periodContaining(PeriodRule.Monthly(1), date(2026, 2, 14))
        assertEquals(DateRange(date(2026, 2, 1), date(2026, 2, 28)), range)
        assertEquals(28, range.lengthInDays)
    }

    @Test
    fun `pay-day month before and after the start day`() {
        val rule = PeriodRule.Monthly(5)
        assertEquals(
            DateRange(date(2026, 9, 5), date(2026, 10, 4)),
            PeriodResolver.periodContaining(rule, date(2026, 10, 4)),
        )
        assertEquals(
            DateRange(date(2026, 10, 5), date(2026, 11, 4)),
            PeriodResolver.periodContaining(rule, date(2026, 10, 5)),
        )
    }

    @Test
    fun `pay-day month across the year boundary`() {
        assertEquals(
            DateRange(date(2025, 12, 25), date(2026, 1, 24)),
            PeriodResolver.periodContaining(PeriodRule.Monthly(25), date(2026, 1, 3)),
        )
    }

    @Test
    fun `fixed length periods forwards and backwards from the anchor`() {
        val rule = PeriodRule.FixedLength(days = 14, anchor = date(2026, 10, 1))
        assertEquals(
            DateRange(date(2026, 10, 1), date(2026, 10, 14)),
            PeriodResolver.periodContaining(rule, date(2026, 10, 1)),
        )
        assertEquals(
            DateRange(date(2026, 10, 15), date(2026, 10, 28)),
            PeriodResolver.periodContaining(rule, date(2026, 10, 20)),
        )
        assertEquals(
            DateRange(date(2026, 9, 17), date(2026, 9, 30)),
            PeriodResolver.periodContaining(rule, date(2026, 9, 30)),
        )
    }

    @Test
    fun `date range membership and validation`() {
        val range = DateRange(date(2026, 1, 1), date(2026, 1, 31))
        assertTrue(date(2026, 1, 1) in range)
        assertTrue(date(2026, 1, 31) in range)
        assertFalse(date(2026, 2, 1) in range)
        assertThrows<IllegalArgumentException> { DateRange(date(2026, 1, 2), date(2026, 1, 1)) }
    }
}
