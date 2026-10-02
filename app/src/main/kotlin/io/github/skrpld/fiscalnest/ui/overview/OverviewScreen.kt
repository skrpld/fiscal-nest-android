/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fiscalnest.core.ForecastResult
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.engine.ForecastOutcome
import io.github.skrpld.fiscalnest.domain.form.SpendingDraft
import io.github.skrpld.fiscalnest.domain.form.SpendingField
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.Spending
import io.github.skrpld.fiscalnest.ui.budget.BudgetBanners
import io.github.skrpld.fiscalnest.ui.budget.CashCard
import io.github.skrpld.fiscalnest.ui.budget.CushionCard
import io.github.skrpld.fiscalnest.ui.budget.PlanCard
import io.github.skrpld.fiscalnest.ui.budget.hasBudgetBanners
import io.github.skrpld.fiscalnest.ui.common.AmountRow
import io.github.skrpld.fiscalnest.ui.common.BannerTone
import io.github.skrpld.fiscalnest.ui.common.FabClearance
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.LocalDateTexts
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.SectionCard
import io.github.skrpld.fiscalnest.ui.common.StatusBanner
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.tabular
import io.github.skrpld.fiscalnest.ui.common.withContentPadding
import io.github.skrpld.fiscalnest.ui.spending.AddSpendingSheet
import java.math.BigDecimal

/** Minimum card width; wider windows show several columns. */
internal val CardColumnMinWidth = 340.dp

@Composable
fun OverviewRoute(
    onAddEvent: () -> Unit,
    onOpenPeriod: (Int) -> Unit,
    onOpenSpendings: () -> Unit,
    viewModel: OverviewViewModel = appViewModel { OverviewViewModel(it.repository, it.dateProvider, it.idGenerator) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDate() }
    OverviewScreen(
        state = state,
        onAddSpending = viewModel::addSpending,
        onAddEvent = onAddEvent,
        onOpenPeriod = onOpenPeriod,
        onOpenSpendings = onOpenSpendings,
    )
}

@Composable
fun OverviewScreen(
    state: OverviewUiState,
    onAddSpending: (SpendingDraft) -> Validation<Spending, SpendingField>,
    onAddEvent: () -> Unit,
    onOpenPeriod: (Int) -> Unit,
    onOpenSpendings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showSpendingSheet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.overview_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state is OverviewUiState.Ready) {
                ExtendedFloatingActionButton(
                    onClick = { showSpendingSheet = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_spending)) },
                )
            }
        },
    ) { padding ->
        when (state) {
            OverviewUiState.Loading -> LoadingContent(Modifier.padding(padding))
            is OverviewUiState.Ready -> OverviewContent(
                state = state,
                padding = padding,
                onAddEvent = onAddEvent,
                onOpenPeriod = onOpenPeriod,
                onOpenSpendings = onOpenSpendings,
            )
        }
    }
    if (showSpendingSheet && state is OverviewUiState.Ready) {
        AddSpendingSheet(
            today = state.today,
            onSubmit = onAddSpending,
            onDismiss = { showSpendingSheet = false },
        )
    }
}

@Composable
private fun OverviewContent(
    state: OverviewUiState.Ready,
    padding: PaddingValues,
    onAddEvent: () -> Unit,
    onOpenPeriod: (Int) -> Unit,
    onOpenSpendings: () -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(CardColumnMinWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding.withContentPadding(extraBottom = FabClearance),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val outcome = state.outcome) {
            is ForecastOutcome.Failure -> item(key = "failure", span = StaggeredGridItemSpan.FullLine) {
                StatusBanner(
                    tone = BannerTone.ERROR,
                    icon = Icons.Rounded.Warning,
                    title = stringResource(R.string.overview_calculation_failed),
                    text = outcome.message,
                )
            }
            is ForecastOutcome.Success -> {
                val current = outcome.current
                item(key = "hero", span = StaggeredGridItemSpan.FullLine) {
                    HeroCard(outcome)
                }
                if (!state.hasEvents) {
                    item(key = "no-events", span = StaggeredGridItemSpan.FullLine) {
                        NoEventsCard(onAddEvent)
                    }
                }
                if (hasBudgetBanners(current.distribution, current.cashFlow)) {
                    item(key = "banners", span = StaggeredGridItemSpan.FullLine) {
                        BudgetBanners(current.distribution, current.cashFlow)
                    }
                }
                item(key = "spending") {
                    SpendingCard(
                        alreadySpent = outcome.alreadySpent,
                        burnRate = current.dailyMetrics.burnRate,
                        recent = state.recentSpendings,
                        onOpenSpendings = onOpenSpendings,
                    )
                }
                item(key = "cash") {
                    CashCard(current.cashFlow, title = stringResource(R.string.cash_title_today))
                }
                item(key = "plan") {
                    PlanCard(current.distribution)
                }
                item(key = "cushion") {
                    CushionCard(current.distribution)
                }
                item(key = "forecast") {
                    ForecastCard(outcome.periods, onOpenPeriod)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(outcome: ForecastOutcome.Success) {
    val current = outcome.current
    val money = LocalMoneyFormatter.current
    val dates = LocalDateTexts.current
    val daily = current.dailyMetrics
    val elapsed = (current.daysElapsed + 1).toFloat() / current.daysInPeriod
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.overview_safe_today), style = MaterialTheme.typography.titleMedium)
            Text(
                text = money.format(daily.dailyCashflow.max(BigDecimal.ZERO)),
                style = MaterialTheme.typography.displaySmall.tabular,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.overview_available_now, money.format(current.cashFlow.available)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.overview_period_progress,
                    dates.range(outcome.period),
                    pluralStringResource(R.plurals.days_left, current.daysRemaining, current.daysRemaining),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            LinearProgressIndicator(
                progress = { elapsed.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroStat(stringResource(R.string.overview_daily_free), daily.dailyActual, Modifier.weight(1f))
                HeroStat(stringResource(R.string.overview_daily_plan), daily.dailyPlan, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, amount: BigDecimal, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = LocalMoneyFormatter.current.format(amount),
            style = MaterialTheme.typography.titleMedium.tabular,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoEventsCard(onAddEvent: () -> Unit) {
    SectionCard(
        title = stringResource(R.string.overview_no_events_title),
        icon = Icons.Rounded.Event,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(stringResource(R.string.overview_no_events_text), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAddEvent) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_add_event))
        }
    }
}

@Composable
private fun SpendingCard(
    alreadySpent: BigDecimal,
    burnRate: BigDecimal,
    recent: List<Spending>,
    onOpenSpendings: () -> Unit,
) {
    val money = LocalMoneyFormatter.current
    val dates = LocalDateTexts.current
    SectionCard(title = stringResource(R.string.spending_title), icon = Icons.Rounded.Receipt) {
        Text(
            text = money.format(alreadySpent),
            style = MaterialTheme.typography.headlineSmall.tabular,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.spending_burn_rate, money.format(burnRate)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recent.isEmpty()) {
            Text(
                text = stringResource(R.string.spending_none_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val noNote = stringResource(R.string.spending_no_note)
            recent.forEach { spending ->
                AmountRow(
                    label = spending.note.ifBlank { noNote },
                    amount = spending.amount,
                    supportingText = dates.dayMonth(spending.date),
                )
            }
        }
        TextButton(onClick = onOpenSpendings) { Text(stringResource(R.string.spending_show_all)) }
    }
}

@Composable
private fun ForecastCard(periods: List<ForecastResult>, onOpenPeriod: (Int) -> Unit) {
    SectionCard(title = stringResource(R.string.forecast_title), icon = Icons.Rounded.Timeline) {
        Text(
            text = stringResource(R.string.forecast_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        periods.forEachIndexed { index, result ->
            ForecastRow(index = index, result = result, onClick = { onOpenPeriod(index) })
        }
    }
}

@Composable
private fun ForecastRow(index: Int, result: ForecastResult, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val dates = LocalDateTexts.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (index == 0) {
                Text(
                    text = stringResource(R.string.forecast_current_period),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(dates.range(result.periodStart, result.periodEnd), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(
                    R.string.forecast_row_details,
                    money.format(result.freeBalance),
                    money.format(result.distribution.cushionCurrent),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = money.format(result.closingBalance),
            style = MaterialTheme.typography.titleSmall.tabular,
            color = if (result.closingBalance.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
