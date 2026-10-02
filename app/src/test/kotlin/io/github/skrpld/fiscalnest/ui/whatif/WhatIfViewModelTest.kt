/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.whatif

import io.github.skrpld.fiscalnest.FixedToday
import io.github.skrpld.fiscalnest.MainDispatcherRule
import io.github.skrpld.fiscalnest.assertDecimal
import io.github.skrpld.fiscalnest.domain.data.InMemoryBudgetRepository
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.WhatIfField
import io.github.skrpld.fiscalnest.sampleData
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WhatIfViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `prefills the stored cushion and waits for amounts`() = runTest {
        val viewModel = WhatIfViewModel(InMemoryBudgetRepository(sampleData()), FixedToday)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals("5000", viewModel.draft.cushionCurrent)
        assertEquals("20000", viewModel.draft.cushionTarget)
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `distributes typed amounts with the stored rules`() = runTest {
        val viewModel = WhatIfViewModel(InMemoryBudgetRepository(sampleData()), FixedToday)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onDraftChange(viewModel.draft.copy(income = "50000", mandatory = "20000", optional = "10000"))

        val result = viewModel.uiState.value.result!!
        assertTrue(result.cushionCrisis)
        assertDecimal("4000", result.cushionTopup)
        assertDecimal("5000", result.piggyBankActual)
        assertDecimal("11000", result.freeRemainder)
    }

    @Test
    fun `reports invalid fields`() = runTest {
        val viewModel = WhatIfViewModel(InMemoryBudgetRepository(sampleData()), FixedToday)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onDraftChange(viewModel.draft.copy(income = "lots"))

        assertEquals(mapOf(WhatIfField.INCOME to FieldError.INVALID_NUMBER), viewModel.uiState.value.errors)
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `fills the form from the current period`() = runTest {
        val viewModel = WhatIfViewModel(InMemoryBudgetRepository(sampleData()), FixedToday)

        viewModel.fillFromCurrentPeriod()

        assertEquals("50000", viewModel.draft.income)
        assertEquals("20000", viewModel.draft.mandatory)
        assertEquals("0", viewModel.draft.optional)

        viewModel.clear()
        assertTrue(viewModel.draft.isEmpty)
    }
}
