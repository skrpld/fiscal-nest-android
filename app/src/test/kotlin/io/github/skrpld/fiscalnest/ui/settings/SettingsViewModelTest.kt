/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.settings

import fiscalnest.core.CashReserve
import fiscalnest.core.PiggyBankMode
import io.github.skrpld.fiscalnest.FixedToday
import io.github.skrpld.fiscalnest.MainDispatcherRule
import io.github.skrpld.fiscalnest.domain.data.AppDataCodec
import io.github.skrpld.fiscalnest.domain.data.InMemoryBudgetRepository
import io.github.skrpld.fiscalnest.domain.form.PiggyBankValues
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.ThemeMode
import io.github.skrpld.fiscalnest.sampleData
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = InMemoryBudgetRepository(sampleData())

    private fun TestScope.createViewModel(messages: MutableList<SettingsMessage>): SettingsViewModel {
        val viewModel = SettingsViewModel(
            repository = repository,
            dateProvider = FixedToday,
            initialData = { AppData() },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.collect { messages += it } }
        return viewModel
    }

    @Test
    fun `updates budget rules`() = runTest {
        val viewModel = createViewModel(mutableListOf())

        viewModel.setPeriod(PeriodRule.Monthly(startDay = 5))
        viewModel.setForecastPeriods(99)
        viewModel.setShowCents(false)
        viewModel.setCashReserve(CashReserve.PIGGY_BANK, enabled = true)
        viewModel.setCashReserve(CashReserve.CUSHION_TOPUP, enabled = false)
        viewModel.setPiggyBank(PiggyBankValues(PiggyBankMode.PERCENT_OF_REMAINDER, BigDecimal("0.1"), BigDecimal.ONE))
        viewModel.setCurrency("EUR")

        val settings = repository.current.settings
        assertEquals(PeriodRule.Monthly(5), settings.period)
        assertEquals(24, settings.forecastPeriods)
        assertEquals(0, settings.moneyScale)
        assertEquals(setOf(CashReserve.PIGGY_BANK), settings.cashReserves)
        assertEquals(PiggyBankMode.PERCENT_OF_REMAINDER, settings.piggyBankMode)
        assertEquals("EUR", settings.currencyCode)
    }

    @Test
    fun `updates appearance`() = runTest {
        val viewModel = createViewModel(mutableListOf())

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setDynamicColor(false)

        assertEquals(ThemeMode.DARK, repository.current.appearance.themeMode)
        assertEquals(false, repository.current.appearance.dynamicColor)
    }

    @Test
    fun `exports and imports a backup`() = runTest {
        val messages = mutableListOf<SettingsMessage>()
        val viewModel = createViewModel(messages)
        val output = ByteArrayOutputStream()

        viewModel.exportBackup { output }
        val backup = output.toByteArray()
        assertEquals(sampleData(), AppDataCodec.decode(backup.decodeToString()))

        viewModel.resetAll()
        assertEquals(AppData(), repository.current)

        viewModel.importBackup { ByteArrayInputStream(backup) }
        assertEquals(sampleData(), repository.current)
        assertEquals(listOf(SettingsMessage.EXPORTED, SettingsMessage.RESET, SettingsMessage.IMPORTED), messages)
    }

    @Test
    fun `rejects broken backups and reports failed files`() = runTest {
        val messages = mutableListOf<SettingsMessage>()
        val viewModel = createViewModel(messages)

        viewModel.importBackup { ByteArrayInputStream("{\"settings\":{\"forecastPeriods\":0}}".encodeToByteArray()) }
        viewModel.importBackup { null }
        viewModel.exportBackup { throw IOException("disk full") }

        assertEquals(sampleData(), repository.current)
        assertEquals(
            listOf(SettingsMessage.IMPORT_FAILED, SettingsMessage.IMPORT_FAILED, SettingsMessage.EXPORT_FAILED),
            messages,
        )
        assertTrue(repository.current.events.isNotEmpty())
    }
}
