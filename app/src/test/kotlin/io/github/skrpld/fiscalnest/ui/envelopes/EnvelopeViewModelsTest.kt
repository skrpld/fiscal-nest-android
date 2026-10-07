/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.envelopes

import io.github.skrpld.fiscalnest.FixedToday
import io.github.skrpld.fiscalnest.MainDispatcherRule
import io.github.skrpld.fiscalnest.Today
import io.github.skrpld.fiscalnest.assertDecimal
import io.github.skrpld.fiscalnest.domain.data.InMemoryBudgetRepository
import io.github.skrpld.fiscalnest.domain.form.EnvelopeField
import io.github.skrpld.fiscalnest.domain.form.EnvelopeOperationDraft
import io.github.skrpld.fiscalnest.domain.form.EnvelopeOperationField
import io.github.skrpld.fiscalnest.domain.form.EnvelopePolicyType
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopePolicy
import io.github.skrpld.fiscalnest.sampleData
import io.github.skrpld.fiscalnest.sequentialIds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class EnvelopeViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cafes = Envelope("cafes", "Cafes", EnvelopePolicy.PeriodLimit(BigDecimal("500")))

    private fun dataWithCafes() = sampleData().copy(
        envelopes = listOf(cafes),
        envelopeOperations = listOf(
            EnvelopeOperation("start", "cafes", EnvelopeOperationType.DEPOSIT, BigDecimal("3000"), Today.minusMonths(1)),
        ),
    )

    @Test
    fun `creates an envelope with its initial balance`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = EnvelopeEditorViewModel(repository, FixedToday, sequentialIds(), envelopeId = null)

        val draft = viewModel.uiState.draft!!
        viewModel.onDraftChange(
            draft.copy(name = "Vacation", policyType = EnvelopePolicyType.UNTIL_TARGET, target = "50000", initialBalance = "1000"),
        )
        viewModel.save(initialNote = "Initial balance")

        assertEquals(EnvelopeEditorResult.SAVED, viewModel.uiState.result)
        val envelope = repository.current.envelopes.single()
        assertEquals(Envelope("id-1", "Vacation", EnvelopePolicy.UntilTarget, BigDecimal("50000")), envelope)
        val deposit = repository.current.envelopeOperations.single()
        assertEquals("id-1", deposit.envelopeId)
        assertEquals(EnvelopeOperationType.DEPOSIT, deposit.type)
        assertEquals(Today, deposit.date)
        assertEquals("Initial balance", deposit.note)
        assertDecimal("1000", deposit.amount)
    }

    @Test
    fun `reports invalid envelope fields`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = EnvelopeEditorViewModel(repository, FixedToday, sequentialIds(), envelopeId = null)

        viewModel.save(initialNote = "")

        assertEquals(FieldError.REQUIRED, viewModel.uiState.errors[EnvelopeField.NAME])
        assertTrue(repository.current.envelopes.isEmpty())
    }

    @Test
    fun `edits and deletes an existing envelope`() = runTest {
        val repository = InMemoryBudgetRepository(dataWithCafes())
        val editor = EnvelopeEditorViewModel(repository, FixedToday, sequentialIds(), envelopeId = "cafes")

        val draft = editor.uiState.draft!!
        assertEquals("500", draft.limit)
        editor.onDraftChange(draft.copy(limit = "800"))
        editor.save(initialNote = "ignored")
        assertEquals(EnvelopePolicy.PeriodLimit(BigDecimal("800")), repository.current.envelopes.single().policy)
        assertEquals(1, repository.current.envelopeOperations.size)

        val deleter = EnvelopeEditorViewModel(repository, FixedToday, sequentialIds(), envelopeId = "cafes")
        deleter.delete()
        assertEquals(EnvelopeEditorResult.DELETED, deleter.uiState.result)
        assertTrue(repository.current.envelopes.isEmpty())
        assertTrue(repository.current.envelopeOperations.isEmpty())
    }

    @Test
    fun `withdrawals follow the envelope policy`() = runTest {
        val repository = InMemoryBudgetRepository(dataWithCafes())
        val viewModel = EnvelopeDetailViewModel(repository, FixedToday, sequentialIds(), envelopeId = "cafes")

        val tooMuch = viewModel.record(EnvelopeOperationType.WITHDRAWAL, EnvelopeOperationDraft(amount = "600", date = Today))
        assertEquals(
            mapOf(EnvelopeOperationField.AMOUNT to FieldError.EXCEEDS_AVAILABLE),
            (tooMuch as Validation.Invalid).errors,
        )

        val allowed = viewModel.record(EnvelopeOperationType.WITHDRAWAL, EnvelopeOperationDraft(amount = "450", date = Today))
        assertTrue(allowed is Validation.Valid)
        val second = viewModel.record(EnvelopeOperationType.WITHDRAWAL, EnvelopeOperationDraft(amount = "100", date = Today))
        assertTrue(second is Validation.Invalid)

        val deposit = viewModel.record(EnvelopeOperationType.DEPOSIT, EnvelopeOperationDraft(amount = "100", date = Today))
        assertTrue(deposit is Validation.Valid)
        assertEquals(3, repository.current.envelopeOperations.size)
    }

    @Test
    fun `deletes and restores an operation`() = runTest {
        val repository = InMemoryBudgetRepository(dataWithCafes())
        val viewModel = EnvelopeDetailViewModel(repository, FixedToday, sequentialIds(), envelopeId = "cafes")
        val operation = repository.current.envelopeOperations.single()

        viewModel.delete(operation)
        assertTrue(repository.current.envelopeOperations.isEmpty())

        viewModel.restore(operation)
        assertEquals(listOf(operation), repository.current.envelopeOperations)
    }
}
