/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.period

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import fiscalnest.core.ForecastResult
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.engine.BudgetEngine
import io.github.skrpld.fiscalnest.domain.engine.ForecastOutcome
import io.github.skrpld.fiscalnest.ui.budget.BalancesCard
import io.github.skrpld.fiscalnest.ui.budget.BudgetBanners
import io.github.skrpld.fiscalnest.ui.budget.CashCard
import io.github.skrpld.fiscalnest.ui.budget.CushionCard
import io.github.skrpld.fiscalnest.ui.budget.DailyMetricsCard
import io.github.skrpld.fiscalnest.ui.budget.PlanCard
import io.github.skrpld.fiscalnest.ui.budget.hasBudgetBanners
import io.github.skrpld.fiscalnest.ui.common.BannerTone
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.LocalDateTexts
import io.github.skrpld.fiscalnest.ui.common.StatusBanner
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.withContentPadding
import io.github.skrpld.fiscalnest.ui.overview.CardColumnMinWidth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * State of the period detail screen.
 */
sealed interface PeriodDetailUiState {
    data object Loading : PeriodDetailUiState

    /** The period no longer exists, e.g. after the forecast horizon was shortened. */
    data object Missing : PeriodDetailUiState

    data class Ready(val index: Int, val result: ForecastResult) : PeriodDetailUiState
}

class PeriodDetailViewModel(
    repository: BudgetRepository,
    dateProvider: DateProvider,
    private val index: Int,
) : ViewModel() {
    val uiState: StateFlow<PeriodDetailUiState> = repository.data
        .map { data ->
            val outcome = BudgetEngine.forecast(data, dateProvider.today())
            val result = (outcome as? ForecastOutcome.Success)?.periods?.getOrNull(index)
            if (result == null) PeriodDetailUiState.Missing else PeriodDetailUiState.Ready(index, result)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodDetailUiState.Loading)
}

@Composable
fun PeriodDetailRoute(
    index: Int,
    onBack: () -> Unit,
    viewModel: PeriodDetailViewModel = appViewModel(key = "period-$index") {
        PeriodDetailViewModel(it.repository, it.dateProvider, index)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PeriodDetailScreen(state, onBack)
}

@Composable
fun PeriodDetailScreen(state: PeriodDetailUiState, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dates = LocalDateTexts.current
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        if (state is PeriodDetailUiState.Ready) {
                            dates.range(state.result.periodStart, state.result.periodEnd)
                        } else {
                            stringResource(R.string.period_detail_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when (state) {
            PeriodDetailUiState.Loading -> LoadingContent(Modifier.padding(padding))
            PeriodDetailUiState.Missing -> StatusBanner(
                tone = BannerTone.INFO,
                icon = Icons.Rounded.Warning,
                title = stringResource(R.string.period_detail_missing),
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            is PeriodDetailUiState.Ready -> PeriodDetailContent(state, padding)
        }
    }
}

@Composable
private fun PeriodDetailContent(state: PeriodDetailUiState.Ready, padding: androidx.compose.foundation.layout.PaddingValues) {
    val result = state.result
    val isCurrent = state.index == 0
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(CardColumnMinWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding.withContentPadding(),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hasBudgetBanners(result.distribution, result.cashFlow)) {
            item(key = "banners", span = StaggeredGridItemSpan.FullLine) {
                BudgetBanners(result.distribution, result.cashFlow)
            }
        }
        item(key = "balances") { BalancesCard(result) }
        item(key = "daily") { DailyMetricsCard(result.dailyMetrics) }
        item(key = "plan") { PlanCard(result.distribution) }
        item(key = "cushion") { CushionCard(result.distribution) }
        item(key = "cash") {
            CashCard(
                cashFlow = result.cashFlow,
                title = stringResource(if (isCurrent) R.string.cash_title_today else R.string.cash_title_period_start),
            )
        }
    }
}
