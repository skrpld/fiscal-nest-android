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
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeStatus
import io.github.skrpld.fiscalnest.domain.envelope.spendingBalance
import io.github.skrpld.fiscalnest.domain.form.OccurrenceDraft
import io.github.skrpld.fiscalnest.domain.form.OccurrenceField
import io.github.skrpld.fiscalnest.domain.form.OccurrenceValues
import io.github.skrpld.fiscalnest.domain.form.SpendingDraft
import io.github.skrpld.fiscalnest.domain.form.SpendingField
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.Spending
import io.github.skrpld.fiscalnest.domain.model.confirmOccurrence
import io.github.skrpld.fiscalnest.domain.model.reopenOccurrence
import io.github.skrpld.fiscalnest.domain.model.skipOccurrence
import io.github.skrpld.fiscalnest.domain.model.startQueue
import io.github.skrpld.fiscalnest.domain.period.PeriodResolver
import io.github.skrpld.fiscalnest.domain.queue.ConfirmationQueue
import io.github.skrpld.fiscalnest.domain.queue.PendingOccurrence
import io.github.skrpld.fiscalnest.ui.spending.SpendingRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * State of the overview tab.
 */
sealed interface OverviewUiState {
    data object Loading : OverviewUiState

    /**
     * @property hasEvents `false` before the user has added any income or expense
     * @property recentSpendings latest spending of the current period, newest first
     * @property envelopes every envelope; money is always kept in them
     * @property pending due event occurrences waiting for the user to confirm or skip them
     */
    data class Ready(
        val today: LocalDate,
        val hasEvents: Boolean,
        val outcome: ForecastOutcome,
        val recentSpendings: List<Spending>,
        val envelopes: List<EnvelopeStatus> = emptyList(),
        val pending: List<PendingOccurrence> = emptyList(),
    ) : OverviewUiState {
        /** Balance of all envelopes, the money the user has; `null` without envelopes. */
        val envelopesTotal: BigDecimal?
            get() = if (envelopes.isEmpty()) null else envelopes.fold(BigDecimal.ZERO) { total, status -> total + status.balance }

        /** Balance of the envelopes for everyday spending; `null` when there are none. */
        val spendingEnvelopesBalance: BigDecimal? get() = envelopes.spendingBalance()
    }
}

class OverviewViewModel(
    private val repository: BudgetRepository,
    private val dateProvider: DateProvider,
    idGenerator: IdGenerator,
) : ViewModel() {
    private val today = MutableStateFlow(dateProvider.today())
    private val spendingRecorder = SpendingRecorder(repository, idGenerator, viewModelScope)

    // Eager, so that confirm() always checks the envelope against the stored operations.
    private val data: StateFlow<AppData?> = repository.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
            envelopes = EnvelopeCalculator.summary(data, date).statuses,
            pending = ConfirmationQueue.pending(data, date),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), OverviewUiState.Loading)

    init {
        viewModelScope.launch {
            repository.update { it.startQueue(PeriodResolver.periodContaining(it.settings.period, dateProvider.today()).start) }
        }
    }

    /** Re-reads the date, so the forecast moves on when the app is resumed on a new day. */
    fun refreshDate() {
        today.value = dateProvider.today()
    }

    fun addSpending(draft: SpendingDraft): Validation<Spending, SpendingField> =
        spendingRecorder.record(draft, today.value)

    /**
     * Moves the money of [occurrence] through the envelope of [draft] when it is valid; an expense
     * must fit what the envelope allows on the occurrence date.
     *
     * @return the validation result, so the form can show field errors
     */
    fun confirm(occurrence: PendingOccurrence, draft: OccurrenceDraft): Validation<OccurrenceValues, OccurrenceField> {
        val current = data.value ?: return Validation.Invalid(emptyMap())
        val available = draft.envelopeId
            ?.let { EnvelopeCalculator.status(current, it, occurrence.date)?.available }
            ?: BigDecimal.ZERO
        val result = draft.validate(isIncome = occurrence.event.kind == EventKind.INCOME, available = available)
        if (result is Validation.Valid) {
            viewModelScope.launch {
                repository.update {
                    it.confirmOccurrence(occurrence.event, occurrence.date, result.value.envelopeId, result.value.amount)
                }
            }
        }
        return result
    }

    /** Records that [occurrence] did not happen. */
    fun skip(occurrence: PendingOccurrence) {
        viewModelScope.launch { repository.update { it.skipOccurrence(occurrence.event.id, occurrence.date) } }
    }

    /** Puts [occurrence] back in the queue, undoing [confirm] or [skip]. */
    fun reopen(occurrence: PendingOccurrence) {
        viewModelScope.launch { repository.update { it.reopenOccurrence(occurrence.event.id, occurrence.date) } }
    }

    private companion object {
        const val RECENT_SPENDINGS = 3
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
