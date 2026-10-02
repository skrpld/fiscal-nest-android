/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.whatif

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fiscalnest.core.DistributionResult
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.form.WhatIfDraft
import io.github.skrpld.fiscalnest.domain.form.WhatIfField
import io.github.skrpld.fiscalnest.ui.budget.BudgetBanners
import io.github.skrpld.fiscalnest.ui.budget.CushionCard
import io.github.skrpld.fiscalnest.ui.budget.PlanCard
import io.github.skrpld.fiscalnest.ui.budget.hasBudgetBanners
import io.github.skrpld.fiscalnest.ui.common.BannerTone
import io.github.skrpld.fiscalnest.ui.common.EmptyState
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.MoneyField
import io.github.skrpld.fiscalnest.ui.common.SectionCard
import io.github.skrpld.fiscalnest.ui.common.StatusBanner
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.tabular
import io.github.skrpld.fiscalnest.ui.common.withContentPadding
import io.github.skrpld.fiscalnest.ui.overview.CardColumnMinWidth

@Composable
fun WhatIfRoute(viewModel: WhatIfViewModel = appViewModel { WhatIfViewModel(it.repository, it.dateProvider) }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WhatIfScreen(
        draft = viewModel.draft,
        state = state,
        onDraftChange = viewModel::onDraftChange,
        onFillFromPeriod = viewModel::fillFromCurrentPeriod,
        onClear = viewModel::clear,
    )
}

@Composable
fun WhatIfScreen(
    draft: WhatIfDraft,
    state: WhatIfUiState,
    onDraftChange: (WhatIfDraft) -> Unit,
    onFillFromPeriod: () -> Unit,
    onClear: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.what_if_title)) },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_clear))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(CardColumnMinWidth),
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = padding.withContentPadding(),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro", span = StaggeredGridItemSpan.FullLine) {
                Text(
                    text = stringResource(R.string.what_if_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item(key = "amounts") {
                SectionCard(title = stringResource(R.string.what_if_amounts), icon = Icons.Rounded.Payments) {
                    MoneyField(
                        value = draft.income,
                        onValueChange = { onDraftChange(draft.copy(income = it)) },
                        label = stringResource(R.string.what_if_income),
                        error = state.errors[WhatIfField.INCOME],
                    )
                    MoneyField(
                        value = draft.mandatory,
                        onValueChange = { onDraftChange(draft.copy(mandatory = it)) },
                        label = stringResource(R.string.what_if_mandatory),
                        error = state.errors[WhatIfField.MANDATORY],
                    )
                    MoneyField(
                        value = draft.optional,
                        onValueChange = { onDraftChange(draft.copy(optional = it)) },
                        label = stringResource(R.string.what_if_optional),
                        error = state.errors[WhatIfField.OPTIONAL],
                    )
                    FilledTonalButton(onClick = onFillFromPeriod) {
                        Icon(Icons.Rounded.Today, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.what_if_fill_from_period))
                    }
                }
            }
            item(key = "cushion") {
                SectionCard(title = stringResource(R.string.what_if_cushion), icon = Icons.Rounded.Security) {
                    MoneyField(
                        value = draft.cushionCurrent,
                        onValueChange = { onDraftChange(draft.copy(cushionCurrent = it)) },
                        label = stringResource(R.string.field_cushion_current),
                        error = state.errors[WhatIfField.CUSHION_CURRENT],
                    )
                    MoneyField(
                        value = draft.cushionTarget,
                        onValueChange = { onDraftChange(draft.copy(cushionTarget = it)) },
                        label = stringResource(R.string.field_cushion_target),
                        error = state.errors[WhatIfField.CUSHION_TARGET],
                        imeAction = ImeAction.Done,
                    )
                }
            }
            val result = state.result
            when {
                state.failure != null -> item(key = "failure", span = StaggeredGridItemSpan.FullLine) {
                    StatusBanner(
                        tone = BannerTone.ERROR,
                        icon = Icons.Rounded.Warning,
                        title = stringResource(R.string.overview_calculation_failed),
                        text = state.failure,
                    )
                }
                result == null -> item(key = "placeholder", span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(
                        icon = Icons.Rounded.Calculate,
                        title = stringResource(R.string.what_if_empty_title),
                        text = stringResource(R.string.what_if_empty_text),
                    )
                }
                else -> {
                    item(key = "result", span = StaggeredGridItemSpan.FullLine) { ResultHero(result) }
                    if (hasBudgetBanners(result, cashFlow = null)) {
                        item(key = "banners", span = StaggeredGridItemSpan.FullLine) {
                            BudgetBanners(result, cashFlow = null)
                        }
                    }
                    item(key = "plan") { PlanCard(result) }
                    item(key = "cushion-result") { CushionCard(result) }
                }
            }
        }
    }
}

@Composable
private fun ResultHero(result: DistributionResult) {
    val money = LocalMoneyFormatter.current
    val negative = result.freeRemainder.signum() < 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (negative) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (negative) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.what_if_free_remainder), style = MaterialTheme.typography.titleMedium)
            Text(
                text = money.format(result.freeRemainder),
                style = MaterialTheme.typography.displaySmall.tabular,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.what_if_result_summary,
                    money.format(result.cushionTopup),
                    money.format(result.piggyBankActual),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
