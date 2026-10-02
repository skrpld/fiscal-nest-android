/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fiscalnest.core.CashFlow
import fiscalnest.core.DailyMetrics
import fiscalnest.core.DistributionResult
import fiscalnest.core.ForecastResult
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.ui.common.AmountRow
import io.github.skrpld.fiscalnest.ui.common.BannerTone
import io.github.skrpld.fiscalnest.ui.common.BarSegment
import io.github.skrpld.fiscalnest.ui.common.DistributionBar
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.LocalPercentFormatter
import io.github.skrpld.fiscalnest.ui.common.SectionCard
import io.github.skrpld.fiscalnest.ui.common.StatusBanner
import io.github.skrpld.fiscalnest.ui.common.tabular
import java.math.BigDecimal
import java.math.MathContext

/**
 * How the period's money is allocated: the remainder hierarchy of the engine.
 */
@Composable
fun PlanCard(distribution: DistributionResult, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val free = distribution.freeRemainder
    SectionCard(title = stringResource(R.string.plan_title), icon = Icons.Rounded.PieChart, modifier = modifier) {
        DistributionBar(
            segments = listOf(
                BarSegment(distribution.totalMandatory, colors.primary),
                BarSegment(distribution.totalOptional, colors.secondary),
                BarSegment(distribution.cushionTopup, colors.tertiary),
                BarSegment(distribution.piggyBankActual, colors.tertiaryContainer),
                BarSegment(free, colors.primaryContainer),
            ),
            modifier = Modifier.padding(vertical = 4.dp),
        )
        AmountRow(stringResource(R.string.plan_income), distribution.totalIncome, emphasized = true)
        AmountRow(stringResource(R.string.plan_mandatory), distribution.totalMandatory, dotColor = colors.primary)
        AmountRow(stringResource(R.string.plan_optional), distribution.totalOptional, dotColor = colors.secondary)
        AmountRow(stringResource(R.string.plan_cushion), distribution.cushionTopup, dotColor = colors.tertiary)
        AmountRow(stringResource(R.string.plan_piggy_bank), distribution.piggyBankActual, dotColor = colors.tertiaryContainer)
        HorizontalDivider(color = colors.outlineVariant)
        AmountRow(
            label = stringResource(R.string.plan_free_remainder),
            amount = free,
            emphasized = true,
            dotColor = colors.primaryContainer,
            negativeIsError = true,
        )
    }
}

/**
 * The cash view: money that has actually moved and what is still safe to spend.
 */
@Composable
fun CashCard(cashFlow: CashFlow, title: String, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    SectionCard(title = title, icon = Icons.Rounded.Payments, modifier = modifier) {
        AmountRow(
            label = stringResource(R.string.cash_received_income),
            amount = cashFlow.receivedIncome,
            supportingText = if (cashFlow.pendingIncome.signum() > 0) {
                stringResource(R.string.cash_pending_income, money.format(cashFlow.pendingIncome))
            } else {
                null
            },
        )
        AmountRow(stringResource(R.string.cash_paid_mandatory), cashFlow.paidMandatory)
        AmountRow(stringResource(R.string.cash_paid_optional), cashFlow.paidOptional)
        AmountRow(stringResource(R.string.cash_already_spent), cashFlow.alreadySpent)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AmountRow(stringResource(R.string.cash_liquid_on_hand), cashFlow.liquidOnHand, emphasized = true, negativeIsError = true)
        AmountRow(
            label = stringResource(R.string.cash_must_reserve),
            amount = cashFlow.mustReserve,
            supportingText = stringResource(R.string.cash_upcoming_mandatory, money.format(cashFlow.upcomingMandatory)),
        )
        AmountRow(stringResource(R.string.cash_available), cashFlow.available, emphasized = true, negativeIsError = true)
    }
}

/**
 * Cushion balance after the period's top-up, against its target.
 */
@Composable
fun CushionCard(distribution: DistributionResult, modifier: Modifier = Modifier) {
    val target = distribution.cushionTarget
    val after = distribution.cushionCurrent
    SectionCard(title = stringResource(R.string.cushion_title), icon = Icons.Rounded.Security, modifier = modifier) {
        if (target.signum() == 0) {
            Text(
                text = stringResource(R.string.cushion_no_target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AmountRow(stringResource(R.string.cushion_balance), after)
        } else {
            CushionProgress(distribution)
        }
    }
}

@Composable
private fun CushionProgress(distribution: DistributionResult) {
    val money = LocalMoneyFormatter.current
    val percent = LocalPercentFormatter.current
    val target = distribution.cushionTarget
    val after = distribution.cushionCurrent
    val fillAfter = after.divide(target, MathContext.DECIMAL64)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.cushion_balance_of_target, money.format(after), money.format(target)),
            style = MaterialTheme.typography.titleLarge.tabular,
            fontWeight = FontWeight.SemiBold,
        )
        LinearProgressIndicator(
            progress = { fillAfter.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
        Text(
            text = stringResource(R.string.cushion_fill_change, percent.format(distribution.cushionFillPct), percent.format(fillAfter)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountRow(stringResource(R.string.cushion_topup), distribution.cushionTopup)
        AmountRow(stringResource(R.string.cushion_still_needed), (target - after).max(BigDecimal.ZERO))
    }
}

/**
 * Daily budget figures of a forecast period.
 */
@Composable
fun DailyMetricsCard(metrics: DailyMetrics, modifier: Modifier = Modifier) {
    SectionCard(title = stringResource(R.string.daily_title), icon = Icons.Rounded.Timeline, modifier = modifier) {
        AmountRow(
            label = stringResource(R.string.daily_cashflow),
            amount = metrics.dailyCashflow,
            emphasized = true,
            negativeIsError = true,
            supportingText = stringResource(R.string.daily_cashflow_hint),
        )
        AmountRow(
            label = stringResource(R.string.daily_actual),
            amount = metrics.dailyActual,
            negativeIsError = true,
            supportingText = stringResource(R.string.daily_actual_hint),
        )
        AmountRow(
            label = stringResource(R.string.daily_plan),
            amount = metrics.dailyPlan,
            negativeIsError = true,
            supportingText = stringResource(R.string.daily_plan_hint),
        )
        AmountRow(
            label = stringResource(R.string.daily_burn_rate),
            amount = metrics.burnRate,
            supportingText = stringResource(R.string.daily_burn_rate_hint),
        )
    }
}

/**
 * Carry-forward balances of a forecast period.
 */
@Composable
fun BalancesCard(result: ForecastResult, modifier: Modifier = Modifier) {
    SectionCard(title = stringResource(R.string.balances_title), icon = Icons.Rounded.Savings, modifier = modifier) {
        AmountRow(
            label = stringResource(R.string.balances_opening),
            amount = result.openingBalance,
            negativeIsError = true,
            supportingText = stringResource(R.string.balances_opening_hint),
        )
        AmountRow(stringResource(R.string.balances_free), result.freeBalance, negativeIsError = true)
        AmountRow(
            label = stringResource(R.string.balances_closing),
            amount = result.closingBalance,
            emphasized = true,
            negativeIsError = true,
            supportingText = stringResource(R.string.balances_closing_hint),
        )
    }
}

/**
 * `true` when [BudgetBanners] has anything to show.
 */
fun hasBudgetBanners(distribution: DistributionResult, cashFlow: CashFlow?): Boolean =
    distribution.expenseCrisis ||
        (cashFlow != null && cashFlow.available.signum() < 0) ||
        distribution.cushionCrisis ||
        distribution.cushionOverfilled ||
        (distribution.piggyBankCappedByAdmissibility && distribution.piggyBankTarget.signum() > 0)

/**
 * Warnings about the plan and, when given, the cash view of a period.
 */
@Composable
fun BudgetBanners(distribution: DistributionResult, cashFlow: CashFlow?, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    val percent = LocalPercentFormatter.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (distribution.expenseCrisis) {
            StatusBanner(
                tone = BannerTone.ERROR,
                icon = Icons.Rounded.Warning,
                title = stringResource(R.string.banner_expense_crisis_title),
                text = stringResource(R.string.banner_expense_crisis_text, money.format(distribution.expenseDeficit)),
            )
        } else if (cashFlow != null && cashFlow.available.signum() < 0) {
            StatusBanner(
                tone = BannerTone.WARNING,
                icon = Icons.Rounded.Warning,
                title = stringResource(R.string.banner_cash_gap_title),
                text = stringResource(R.string.banner_cash_gap_text, money.format(cashFlow.available.negate())),
            )
        }
        if (distribution.cushionCrisis) {
            val level = distribution.activeCriticalityLevel.orEmpty()
            StatusBanner(
                tone = BannerTone.WARNING,
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.banner_cushion_crisis_title, percent.format(distribution.cushionFillPct)),
                text = if (distribution.cushionTopup.signum() > 0) {
                    stringResource(R.string.banner_cushion_crisis_topup, level, money.format(distribution.cushionTopup))
                } else {
                    stringResource(R.string.banner_cushion_crisis_no_topup, level)
                },
            )
        }
        if (distribution.cushionOverfilled) {
            StatusBanner(
                tone = BannerTone.INFO,
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.banner_cushion_overfilled_title),
                text = stringResource(R.string.banner_cushion_overfilled_text),
            )
        }
        if (distribution.piggyBankCappedByAdmissibility && distribution.piggyBankTarget.signum() > 0) {
            StatusBanner(
                tone = BannerTone.INFO,
                icon = Icons.Rounded.Savings,
                title = stringResource(R.string.banner_piggy_capped_title),
                text = stringResource(
                    R.string.banner_piggy_capped_text,
                    money.format(distribution.piggyBankActual),
                    money.format(distribution.piggyBankTarget),
                ),
            )
        }
    }
}
