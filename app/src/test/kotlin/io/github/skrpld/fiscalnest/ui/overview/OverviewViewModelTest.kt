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
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.OccurrenceDraft
import io.github.skrpld.fiscalnest.domain.form.OccurrenceField
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
import java.time.LocalDate

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

        val result = viewModel.addSpending(SpendingDraft(amount = "3500", note = "Groceries", date = Today, envelopeId = "card"))

        assertTrue(result is Validation.Valid)
        assertEquals(1, repository.current.spendings.size)
        assertDecimal("26500", EnvelopeCalculator.status(repository.current, "card", Today)!!.balance)
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
        assertEquals(FieldError.REQUIRED, errors[SpendingField.ENVELOPE])
        assertEquals(FieldError.IN_THE_FUTURE, errors[SpendingField.DATE])
        assertTrue(repository.current.spendings.isEmpty())
    }

    @Test
    fun `spending cannot take more than the envelope has`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = OverviewViewModel(repository, FixedToday, sequentialIds())

        val result = viewModel.addSpending(SpendingDraft(amount = "30001", date = Today, envelopeId = "card"))

        assertEquals(FieldError.EXCEEDS_AVAILABLE, (result as Validation.Invalid).errors[SpendingField.AMOUNT])
        assertTrue(repository.current.spendings.isEmpty())
    }

    @Test
    fun `due income and expenses wait for confirmation`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = OverviewViewModel(repository, FixedToday, sequentialIds())
        subscribe(viewModel)

        assertEquals(LocalDate.of(2026, 8, 1), repository.current.queueStart)
        val state = viewModel.uiState.value as OverviewUiState.Ready
        assertEquals(listOf("salary", "rent"), state.pending.map { it.event.id })
        assertDecimal("30000", state.envelopesTotal!!)
        assertDecimal("30000", state.spendingEnvelopesBalance!!)
    }

    @Test
    fun `confirming moves the money, an expense must fit the envelope`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = OverviewViewModel(repository, FixedToday, sequentialIds())
        subscribe(viewModel)
        fun pending() = (viewModel.uiState.value as OverviewUiState.Ready).pending
        fun balance() = EnvelopeCalculator.status(repository.current, "card", Today)!!.balance

        val rent = pending().first { it.event.id == "rent" }
        val tooMuch = viewModel.confirm(rent, OccurrenceDraft(amount = "30001", envelopeId = "card"))
        assertEquals(FieldError.EXCEEDS_AVAILABLE, (tooMuch as Validation.Invalid).errors[OccurrenceField.AMOUNT])
        assertDecimal("30000", balance())

        val salary = pending().first { it.event.id == "salary" }
        assertTrue(viewModel.confirm(salary, OccurrenceDraft(amount = "52000", envelopeId = "card")) is Validation.Valid)
        assertDecimal("82000", balance())
        assertTrue(viewModel.confirm(rent, OccurrenceDraft.from(rent.event)) is Validation.Valid)
        assertDecimal("62000", balance())
        assertTrue(pending().isEmpty())

        viewModel.reopen(rent)
        assertDecimal("82000", balance())
        assertEquals(listOf("rent"), pending().map { it.event.id })
    }

    @Test
    fun `skipping leaves the money alone`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = OverviewViewModel(repository, FixedToday, sequentialIds())
        subscribe(viewModel)
        val rent = (viewModel.uiState.value as OverviewUiState.Ready).pending.first { it.event.id == "rent" }

        viewModel.skip(rent)

        assertEquals(listOf("salary"), (viewModel.uiState.value as OverviewUiState.Ready).pending.map { it.event.id })
        assertEquals(sampleData().envelopeOperations, repository.current.envelopeOperations)
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
