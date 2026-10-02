/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.overview

import io.github.skrpld.fiscalnest.FixedToday
import io.github.skrpld.fiscalnest.MainDispatcherRule
import io.github.skrpld.fiscalnest.Today
import io.github.skrpld.fiscalnest.assertDecimal
import io.github.skrpld.fiscalnest.domain.data.InMemoryBudgetRepository
import io.github.skrpld.fiscalnest.domain.engine.ForecastOutcome
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.SpendingDraft
import io.github.skrpld.fiscalnest.domain.form.SpendingField
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.sampleData
import io.github.skrpld.fiscalnest.sequentialIds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OverviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun TestScope.subscribe(viewModel: OverviewViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun OverviewViewModel.success(): ForecastOutcome.Success =
        (uiState.value as OverviewUiState.Ready).outcome as ForecastOutcome.Success

    @Test
    fun `forecasts the stored budget`() = runTest {
        val viewModel = OverviewViewModel(InMemoryBudgetRepository(sampleData()), FixedToday, sequentialIds())
        subscribe(viewModel)

        val state = viewModel.uiState.value as OverviewUiState.Ready
        assertTrue(state.hasEvents)
        assertEquals(Today, state.today)
        val outcome = viewModel.success()
        assertEquals(3, outcome.periods.size)
        // 50 000 received - 20 000 rent paid - 4 000 cushion top-up reserved, over 25 days.
        assertDecimal("26000", outcome.current.cashFlow.available)
        assertDecimal("1040", outcome.current.dailyMetrics.dailyCashflow)
    }

    @Test
    fun `logged spending lowers the daily budget`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = OverviewViewModel(repository, FixedToday, sequentialIds())
        subscribe(viewModel)

        val result = viewModel.addSpending(SpendingDraft(amount = "3500", note = "Groceries", date = Today))

        assertTrue(result is Validation.Valid)
        assertEquals(1, repository.current.spendings.size)
        val outcome = viewModel.success()
        assertDecimal("3500", outcome.alreadySpent)
        assertDecimal("900", outcome.current.dailyMetrics.dailyCashflow)
        assertEquals(listOf("Groceries"), (viewModel.uiState.value as OverviewUiState.Ready).recentSpendings.map { it.note })
    }

    @Test
    fun `invalid spending is rejected and not stored`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = OverviewViewModel(repository, FixedToday, sequentialIds())

        val result = viewModel.addSpending(SpendingDraft(amount = "", date = Today.plusDays(1)))

        val errors = (result as Validation.Invalid).errors
        assertEquals(FieldError.REQUIRED, errors[SpendingField.AMOUNT])
        assertEquals(FieldError.IN_THE_FUTURE, errors[SpendingField.DATE])
        assertTrue(repository.current.spendings.isEmpty())
    }

    @Test
    fun `reports an empty budget`() = runTest {
        val viewModel = OverviewViewModel(InMemoryBudgetRepository(AppData()), FixedToday, sequentialIds())
        subscribe(viewModel)

        val state = viewModel.uiState.value as OverviewUiState.Ready
        assertFalse(state.hasEvents)
        assertTrue(state.outcome is ForecastOutcome.Success)
    }
}
