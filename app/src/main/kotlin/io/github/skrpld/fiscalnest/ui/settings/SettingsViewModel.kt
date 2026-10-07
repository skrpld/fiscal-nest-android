/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fiscalnest.core.CashReserve
import io.github.skrpld.fiscalnest.domain.data.AppDataCodec
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.form.CushionValues
import io.github.skrpld.fiscalnest.domain.form.PiggyBankValues
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.AppearanceSettings
import io.github.skrpld.fiscalnest.domain.model.BudgetSettings
import io.github.skrpld.fiscalnest.domain.model.Limits
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.ThemeMode
import io.github.skrpld.fiscalnest.domain.model.updateAppearance
import io.github.skrpld.fiscalnest.domain.model.updateSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate

/**
 * State of the settings tab.
 */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    /** @property cushionFromEnvelopes balance of the cushion envelopes; `null` when there are none */
    data class Ready(
        val today: LocalDate,
        val settings: BudgetSettings,
        val appearance: AppearanceSettings,
        val cushionFromEnvelopes: BigDecimal? = null,
    ) : SettingsUiState
}

/** One-off results shown as snackbars. */
enum class SettingsMessage { EXPORTED, EXPORT_FAILED, IMPORTED, IMPORT_FAILED, RESET }

/**
 * @param initialData data of a fresh installation, used by [resetAll]
 * @param ioDispatcher dispatcher for reading and writing backup files
 */
class SettingsViewModel(
    private val repository: BudgetRepository,
    private val dateProvider: DateProvider,
    private val initialData: () -> AppData,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = repository.data
        .map { SettingsUiState.Ready(dateProvider.today(), it.settings, it.appearance, EnvelopeCalculator.cushionBalance(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    private val messageChannel = Channel<SettingsMessage>(Channel.BUFFERED)

    /** Results of export, import and reset, each delivered once. */
    val messages: Flow<SettingsMessage> = messageChannel.receiveAsFlow()

    fun setPeriod(rule: PeriodRule) = updateSettings { it.copy(period = rule) }

    fun setForecastPeriods(count: Int) = updateSettings {
        it.copy(forecastPeriods = count.coerceIn(Limits.MIN_FORECAST_PERIODS, Limits.MAX_FORECAST_PERIODS))
    }

    fun setCurrency(code: String?) = updateSettings { it.copy(currencyCode = code) }

    fun setShowCents(enabled: Boolean) = updateSettings { it.copy(moneyScale = if (enabled) 2 else 0) }

    fun setCushion(values: CushionValues) = updateSettings {
        it.copy(cushionCurrent = values.current, cushionTarget = values.target)
    }

    fun setPiggyBank(values: PiggyBankValues) = updateSettings {
        it.copy(piggyBankMode = values.mode, piggyBankTarget = values.target, piggyBankAdmissibility = values.admissibility)
    }

    fun setCashReserve(reserve: CashReserve, enabled: Boolean) = updateSettings {
        it.copy(cashReserves = if (enabled) it.cashReserves + reserve else it.cashReserves - reserve)
    }

    fun setThemeMode(mode: ThemeMode) = updateAppearance { it.copy(themeMode = mode) }

    fun setDynamicColor(enabled: Boolean) = updateAppearance { it.copy(dynamicColor = enabled) }

    /** Writes a JSON backup of all data to the stream returned by [openStream]. */
    fun exportBackup(openStream: () -> OutputStream?) {
        viewModelScope.launch {
            val json = AppDataCodec.encode(repository.data.first(), pretty = true)
            val written = withContext(ioDispatcher) {
                try {
                    openStream()?.use { it.write(json.encodeToByteArray()) } != null
                } catch (e: IOException) {
                    false
                } catch (e: SecurityException) {
                    false
                }
            }
            messageChannel.send(if (written) SettingsMessage.EXPORTED else SettingsMessage.EXPORT_FAILED)
        }
    }

    /** Replaces all data with the backup read from the stream returned by [openStream]. */
    fun importBackup(openStream: () -> InputStream?) {
        viewModelScope.launch {
            val text = withContext(ioDispatcher) {
                try {
                    openStream()?.use { it.readBytes().decodeToString() }
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }
            val data = text?.let { AppDataCodec.decodeBackup(it).getOrNull() }
            if (data == null) {
                messageChannel.send(SettingsMessage.IMPORT_FAILED)
            } else {
                repository.update { data }
                messageChannel.send(SettingsMessage.IMPORTED)
            }
        }
    }

    /** Deletes all events, spending and settings. */
    fun resetAll() {
        viewModelScope.launch {
            repository.update { initialData() }
            messageChannel.send(SettingsMessage.RESET)
        }
    }

    private fun updateSettings(transform: (BudgetSettings) -> BudgetSettings) {
        viewModelScope.launch { repository.update { it.updateSettings(transform) } }
    }

    private fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch { repository.update { it.updateAppearance(transform) } }
    }
}
