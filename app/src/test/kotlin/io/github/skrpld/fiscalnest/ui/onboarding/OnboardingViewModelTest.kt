/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.onboarding

import io.github.skrpld.fiscalnest.FixedToday
import io.github.skrpld.fiscalnest.MainDispatcherRule
import io.github.skrpld.fiscalnest.assertDecimal
import io.github.skrpld.fiscalnest.domain.data.InMemoryBudgetRepository
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.QuickSetupDraft
import io.github.skrpld.fiscalnest.domain.form.QuickSetupField
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.sequentialIds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = InMemoryBudgetRepository(AppData(onboardingCompleted = false))
    private val viewModel = OnboardingViewModel(repository, FixedToday, sequentialIds())

    @Test
    fun `applies the quick setup`() = runTest {
        val result = viewModel.finishWithSetup(QuickSetupDraft(income = "60000", payDay = "25"), incomeName = "Salary", envelopeName = "Main card")

        assertTrue(result is Validation.Valid)
        val data = repository.current
        assertTrue(data.onboardingCompleted)
        val income = data.events.single()
        assertEquals("Salary", income.name)
        assertEquals(EventKind.INCOME, income.kind)
        assertDecimal("60000", income.amount)
        assertEquals(PeriodRule.Monthly(25), data.settings.period)
        val card = data.envelopes.single()
        assertEquals("Main card", card.name)
        assertEquals(card.id, income.envelopeId)
    }

    @Test
    fun `keeps the guide open while the setup is invalid`() = runTest {
        val result = viewModel.finishWithSetup(QuickSetupDraft(income = "60000"), incomeName = "Salary", envelopeName = "Main card")

        assertEquals(mapOf(QuickSetupField.PAY_DAY to FieldError.REQUIRED), (result as Validation.Invalid).errors)
        assertFalse(repository.current.onboardingCompleted)
        assertTrue(repository.current.events.isEmpty())
    }

    @Test
    fun `skipping only finishes the guide`() = runTest {
        viewModel.finish()

        assertEquals(AppData(onboardingCompleted = true), repository.current)
    }
}
