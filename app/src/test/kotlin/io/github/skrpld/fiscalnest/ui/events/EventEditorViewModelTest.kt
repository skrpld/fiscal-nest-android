/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.events

import io.github.skrpld.fiscalnest.FixedToday
import io.github.skrpld.fiscalnest.MainDispatcherRule
import io.github.skrpld.fiscalnest.Rent
import io.github.skrpld.fiscalnest.Salary
import io.github.skrpld.fiscalnest.Today
import io.github.skrpld.fiscalnest.assertDecimal
import io.github.skrpld.fiscalnest.domain.data.InMemoryBudgetRepository
import io.github.skrpld.fiscalnest.domain.form.EventField
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.RecurrenceType
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import io.github.skrpld.fiscalnest.sampleData
import io.github.skrpld.fiscalnest.sequentialIds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EventEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `creates a new event`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = EventEditorViewModel(repository, FixedToday, sequentialIds(), eventId = null, EventKind.INCOME)

        val draft = viewModel.uiState.draft!!
        assertTrue(viewModel.uiState.isNew)
        assertEquals(EventKind.INCOME, draft.kind)
        assertEquals(Today, draft.startDate)
        assertEquals("card", draft.envelopeId)
        assertEquals(listOf("card"), viewModel.uiState.envelopes.map { it.envelope.id })

        viewModel.onDraftChange(draft.copy(name = "Bonus", amount = "10 000", recurrenceType = RecurrenceType.ONCE))
        viewModel.save()

        assertTrue(viewModel.uiState.isDone)
        val saved = repository.current.events.last()
        assertEquals("id-1", saved.id)
        assertEquals("Bonus", saved.name)
        assertDecimal("10000", saved.amount)
        assertEquals(Recurrence.Once, saved.recurrence)
        assertEquals("card", saved.envelopeId)
    }

    @Test
    fun `an event saved without an envelope needs one when edited`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData().let { data -> data.copy(events = data.events.map { it.copy(envelopeId = null) }) })
        val viewModel = EventEditorViewModel(repository, FixedToday, sequentialIds(), eventId = "rent", initialKind = null)

        viewModel.save()
        assertEquals(FieldError.REQUIRED, viewModel.uiState.errors[EventField.ENVELOPE])
        assertFalse(viewModel.uiState.isDone)

        viewModel.onDraftChange(viewModel.uiState.draft!!.copy(envelopeId = "card"))
        viewModel.save()
        assertEquals("card", repository.current.events.first { it.id == "rent" }.envelopeId)
    }

    @Test
    fun `shows errors after a failed save and revalidates while typing`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = EventEditorViewModel(repository, FixedToday, sequentialIds(), eventId = null, initialKind = null)

        viewModel.save()
        assertEquals(FieldError.REQUIRED, viewModel.uiState.errors[EventField.NAME])
        assertEquals(FieldError.REQUIRED, viewModel.uiState.errors[EventField.AMOUNT])
        assertFalse(viewModel.uiState.isDone)

        viewModel.onDraftChange(viewModel.uiState.draft!!.copy(name = "Gym"))
        assertNull(viewModel.uiState.errors[EventField.NAME])
        assertEquals(FieldError.REQUIRED, viewModel.uiState.errors[EventField.AMOUNT])
        assertEquals(2, repository.current.events.size)
    }

    @Test
    fun `edits an existing event in place`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = EventEditorViewModel(repository, FixedToday, sequentialIds(), eventId = "rent", initialKind = null)

        val draft = viewModel.uiState.draft!!
        assertFalse(viewModel.uiState.isNew)
        assertEquals("Rent", draft.name)
        assertEquals("20000", draft.amount)
        assertEquals("5", draft.dayOfMonth)

        viewModel.onDraftChange(draft.copy(amount = "21000"))
        viewModel.save()

        assertEquals(listOf("salary", "rent"), repository.current.events.map { it.id })
        assertEquals(Rent.copy(amount = java.math.BigDecimal("21000")), repository.current.events[1])
    }

    @Test
    fun `deletes an event`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = EventEditorViewModel(repository, FixedToday, sequentialIds(), eventId = "rent", initialKind = null)

        viewModel.delete()

        assertTrue(viewModel.uiState.isDone)
        assertEquals(listOf(Salary), repository.current.events)
    }

    @Test
    fun `closes when the event no longer exists`() = runTest {
        val viewModel = EventEditorViewModel(
            InMemoryBudgetRepository(sampleData()),
            FixedToday,
            sequentialIds(),
            eventId = "missing",
            initialKind = null,
        )
        assertTrue(viewModel.uiState.isDone)
    }
}
