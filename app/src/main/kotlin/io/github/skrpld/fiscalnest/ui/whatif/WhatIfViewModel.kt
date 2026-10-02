/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.whatif

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fiscalnest.core.DistributionResult
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.engine.BudgetEngine
import io.github.skrpld.fiscalnest.domain.engine.ForecastOutcome
import io.github.skrpld.fiscalnest.domain.engine.WhatIfOutcome
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.form.WhatIfDraft
import io.github.skrpld.fiscalnest.domain.form.WhatIfField
import io.github.skrpld.fiscalnest.domain.input.DecimalInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Calculated part of the what-if tab; the form itself is [WhatIfViewModel.draft].
 *
 * @property result distribution of the typed amounts; `null` until an amount is typed or while
 * a field is invalid
 * @property failure engine validation message when the budget rules are invalid
 */
data class WhatIfUiState(
    val errors: Map<WhatIfField, FieldError> = emptyMap(),
    val result: DistributionResult? = null,
    val failure: String? = null,
)

class WhatIfViewModel(
    private val repository: BudgetRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {
    /** The form. Compose state, so text fields update synchronously while typing. */
    var draft by mutableStateOf(WhatIfDraft())
        private set

    private val draftFlow = MutableStateFlow(draft)

    val uiState: StateFlow<WhatIfUiState> = combine(draftFlow, repository.data) { draft, data ->
        when (val validation = draft.validate()) {
            is Validation.Invalid -> WhatIfUiState(errors = validation.errors)
            is Validation.Valid -> when {
                !draft.hasAmounts -> WhatIfUiState()
                else -> when (val outcome = BudgetEngine.whatIf(validation.value, data.settings)) {
                    is WhatIfOutcome.Success -> WhatIfUiState(result = outcome.result)
                    is WhatIfOutcome.Failure -> WhatIfUiState(failure = outcome.message)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhatIfUiState())

    init {
        viewModelScope.launch {
            val settings = repository.data.first().settings
            if (draft.isEmpty) {
                onDraftChange(
                    draft.copy(
                        cushionCurrent = DecimalInput.formatAmount(settings.cushionCurrent),
                        cushionTarget = DecimalInput.formatAmount(settings.cushionTarget),
                    ),
                )
            }
        }
    }

    fun onDraftChange(newDraft: WhatIfDraft) {
        draft = newDraft
        draftFlow.value = newDraft
    }

    /** Fills the form with the plan totals of the current period and the stored cushion. */
    fun fillFromCurrentPeriod() {
        viewModelScope.launch {
            val data = repository.data.first()
            val outcome = BudgetEngine.forecast(data, dateProvider.today())
            if (outcome is ForecastOutcome.Success) {
                val plan = outcome.current.distribution
                onDraftChange(
                    WhatIfDraft(
                        income = DecimalInput.formatAmount(plan.totalIncome.max(BigDecimal.ZERO)),
                        mandatory = DecimalInput.formatAmount(plan.totalMandatory),
                        optional = DecimalInput.formatAmount(plan.totalOptional),
                        cushionCurrent = DecimalInput.formatAmount(data.settings.cushionCurrent),
                        cushionTarget = DecimalInput.formatAmount(data.settings.cushionTarget),
                    ),
                )
            }
        }
    }

    fun clear() {
        onDraftChange(WhatIfDraft())
    }
}
