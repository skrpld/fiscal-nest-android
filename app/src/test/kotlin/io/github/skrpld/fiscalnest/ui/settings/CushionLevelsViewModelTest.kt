/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.settings

import fiscalnest.core.TopupMode
import io.github.skrpld.fiscalnest.MainDispatcherRule
import io.github.skrpld.fiscalnest.domain.data.InMemoryBudgetRepository
import io.github.skrpld.fiscalnest.domain.form.CushionLevelDraft
import io.github.skrpld.fiscalnest.domain.form.CushionLevelField
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.BudgetSettings
import io.github.skrpld.fiscalnest.domain.model.updateSettings
import io.github.skrpld.fiscalnest.sampleData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CushionLevelsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `adds a level in threshold order`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = CushionLevelsViewModel(repository)

        val result = viewModel.save(null, CushionLevelDraft("Almost", "90", TopupMode.PERCENT_OF_REMAINDER, "5", "20"))

        assertEquals(Validation.Valid::class, result::class)
        assertEquals(listOf("Critical", "Warning", "Almost"), repository.current.settings.cushionLevels.map { it.name })
    }

    @Test
    fun `rejects a duplicate threshold but allows keeping its own`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = CushionLevelsViewModel(repository)

        val duplicate = viewModel.save(null, CushionLevelDraft("Copy", "70", TopupMode.PERCENT_OF_TARGET, "10", "50"))
        assertEquals(mapOf(CushionLevelField.MAX_FILL to FieldError.DUPLICATE), (duplicate as Validation.Invalid).errors)

        val renamed = viewModel.save(1, CushionLevelDraft("Low", "70", TopupMode.PERCENT_OF_REMAINDER, "10", "50"))
        assertEquals(Validation.Valid::class, renamed::class)
        assertEquals(listOf("Critical", "Low"), repository.current.settings.cushionLevels.map { it.name })
    }

    @Test
    fun `keeps the last level`() = runTest {
        val single = sampleData().updateSettings { it.copy(cushionLevels = BudgetSettings.defaultCushionLevels().take(1)) }
        val repository = InMemoryBudgetRepository(single)
        val viewModel = CushionLevelsViewModel(repository)

        viewModel.delete(0)

        assertEquals(1, repository.current.settings.cushionLevels.size)
    }

    @Test
    fun `deletes a level`() = runTest {
        val repository = InMemoryBudgetRepository(sampleData())
        val viewModel = CushionLevelsViewModel(repository)

        viewModel.delete(0)

        assertEquals(listOf("Warning"), repository.current.settings.cushionLevels.map { it.name })
    }
}
