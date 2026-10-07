/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.input

import io.github.skrpld.fiscalnest.domain.assertDecimal
import io.github.skrpld.fiscalnest.domain.dec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class DecimalInputTest {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "1234|1234",
            "1234.5|1234.5",
            "1234,5|1234.5",
            "1 234,56|1234.56",
            "1 234,56|1234.56",
            "1,234.56|1234.56",
            "1.234,56|1234.56",
            "1,234,567|1234567",
            "1.234.567|1234567",
            "12.|12",
            ".5|0.5",
            "007|7",
            "0|0",
            "١٢٣٫٤|123.4",
            "١٬٢٣٤|1234",
            "۱۲|12",
        ],
    )
    fun `parses common notations`(text: String, expected: String) {
        assertDecimal(expected, DecimalInput.parse(text)!!)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "-5", "abc", "1e5", ".", ",", ",,", "1.2,3.4", "12345678901234", "1.23456"])
    fun `rejects invalid input`(text: String) {
        assertNull(DecimalInput.parse(text))
    }

    @Test
    fun `parses percentages into ratios`() {
        assertDecimal("0.3", DecimalInput.parsePercent("30")!!)
        assertDecimal("0.125", DecimalInput.parsePercent("12,5")!!)
        assertDecimal("1", DecimalInput.parsePercent("100")!!)
        assertNull(DecimalInput.parsePercent("100.5"))
    }

    @Test
    fun `formats values for input fields`() {
        assertEquals("50000", DecimalInput.formatAmount(dec("50000.00")))
        assertEquals("12.5", DecimalInput.formatAmount(dec("12.50")))
        assertEquals("30", DecimalInput.formatPercent(dec("0.30")))
        assertEquals("12.5", DecimalInput.formatPercent(dec("0.125")))
        assertEquals("0", DecimalInput.formatPercent(dec("0.00")))
    }
}
