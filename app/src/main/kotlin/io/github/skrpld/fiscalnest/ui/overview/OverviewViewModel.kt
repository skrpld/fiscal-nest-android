/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.engine.BudgetEngine
import io.github.skrpld.fiscalnest.domain.engine.ForecastOutcome
import io.github.skrpld.fiscalnest.domain.form.SpendingDraft
import io.github.skrpld.fiscalnest.domain.form.SpendingField
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.Spending
import io.github.skrpld.fiscalnest.ui.spending.SpendingRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * State of the overview tab.
 */
sealed interface OverviewUiState {
    data object Loading : OverviewUiState

    /**
     * @property hasEvents `false` before the user has added any income or expense
     * @property recentSpendings latest spending of the current period, newest first
     */
    data class Ready(
        val today: LocalDate,
        val hasEvents: Boolean,
        val outcome: ForecastOutcome,
        val recentSpendings: List<Spending>,
    ) : OverviewUiState
}

class OverviewViewModel(
    repository: BudgetRepository,
    private val dateProvider: DateProvider,
    idGenerator: IdGenerator,
) : ViewModel() {
    private val today = MutableStateFlow(dateProvider.today())
    private val spendingRecorder = SpendingRecorder(repository, idGenerator)

    val uiState: StateFlow<OverviewUiState> = combine(repository.data, today) { data, date ->
        val outcome = BudgetEngine.forecast(data, date)
        val recent = if (outcome is ForecastOutcome.Success) {
            data.spendings
                .filter { it.date in outcome.period && it.date <= date }
                .sortedWith(compareByDescending<Spending> { it.date })
                .take(RECENT_SPENDINGS)
        } else {
            emptyList()
        }
        OverviewUiState.Ready(
            today = date,
            hasEvents = data.events.isNotEmpty(),
            outcome = outcome,
            recentSpendings = recent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), OverviewUiState.Loading)

    /** Re-reads the date, so the forecast moves on when the app is resumed on a new day. */
    fun refreshDate() {
        today.value = dateProvider.today()
    }

    fun addSpending(draft: SpendingDraft): Validation<Spending, SpendingField> =
        spendingRecorder.record(draft, today.value, viewModelScope)

    private companion object {
        const val RECENT_SPENDINGS = 3
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
