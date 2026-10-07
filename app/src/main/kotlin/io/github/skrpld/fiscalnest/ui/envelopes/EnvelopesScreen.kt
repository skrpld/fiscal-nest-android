/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.envelopes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeStatus
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopesSummary
import io.github.skrpld.fiscalnest.ui.common.EmptyState
import io.github.skrpld.fiscalnest.ui.common.FabClearance
import io.github.skrpld.fiscalnest.ui.common.IconBadge
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.tabular
import io.github.skrpld.fiscalnest.ui.common.withContentPadding
import io.github.skrpld.fiscalnest.ui.overview.CardColumnMinWidth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * State of the envelopes tab.
 */
sealed interface EnvelopesUiState {
    data object Loading : EnvelopesUiState

    data class Ready(val summary: EnvelopesSummary) : EnvelopesUiState
}

class EnvelopesViewModel(repository: BudgetRepository, private val dateProvider: DateProvider) : ViewModel() {
    private val today = MutableStateFlow(dateProvider.today())

    val uiState: StateFlow<EnvelopesUiState> = combine(repository.data, today) { data, date ->
        EnvelopesUiState.Ready(EnvelopeCalculator.summary(data, date))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnvelopesUiState.Loading)

    /** Re-reads the date, so locks and period limits move on when the app is resumed on a new day. */
    fun refreshDate() {
        today.value = dateProvider.today()
    }
}

@Composable
fun EnvelopesRoute(
    onOpenEnvelope: (String) -> Unit,
    onAddEnvelope: () -> Unit,
    viewModel: EnvelopesViewModel = appViewModel { EnvelopesViewModel(it.repository, it.dateProvider) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDate() }
    EnvelopesScreen(state, onOpenEnvelope, onAddEnvelope)
}

@Composable
fun EnvelopesScreen(
    state: EnvelopesUiState,
    onOpenEnvelope: (String) -> Unit,
    onAddEnvelope: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.envelopes_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddEnvelope,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_envelope)) },
            )
        },
    ) { padding ->
        when (state) {
            EnvelopesUiState.Loading -> LoadingContent(Modifier.padding(padding))
            is EnvelopesUiState.Ready -> if (state.summary.statuses.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Mail,
                    title = stringResource(R.string.envelopes_empty_title),
                    text = stringResource(R.string.envelopes_empty_text),
                    modifier = Modifier.padding(padding),
                ) {
                    FilledTonalButton(onClick = onAddEnvelope) { Text(stringResource(R.string.action_add_envelope)) }
                }
            } else {
                EnvelopeGrid(state.summary, padding, onOpenEnvelope)
            }
        }
    }
}

@Composable
private fun EnvelopeGrid(summary: EnvelopesSummary, padding: PaddingValues, onOpenEnvelope: (String) -> Unit) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(CardColumnMinWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding.withContentPadding(extraBottom = FabClearance),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "total", span = StaggeredGridItemSpan.FullLine) {
            TotalCard(summary)
        }
        items(summary.statuses, key = { it.envelope.id }) { status ->
            EnvelopeCard(status, onClick = { onOpenEnvelope(status.envelope.id) })
        }
    }
}

@Composable
private fun TotalCard(summary: EnvelopesSummary) {
    val money = LocalMoneyFormatter.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.envelopes_total), style = MaterialTheme.typography.titleMedium)
            Text(
                text = money.format(summary.total),
                style = MaterialTheme.typography.displaySmall.tabular,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.envelopes_total_available, money.format(summary.available)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.envelopes_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun EnvelopeCard(status: EnvelopeStatus, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(status.envelope.policy.icon, colors.secondaryContainer, colors.onSecondaryContainer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = status.envelope.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = envelopePolicyText(status.envelope),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = money.format(status.balance),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            EnvelopeProgress(status)
            Text(
                text = envelopeLockText(status.lock)
                    ?: stringResource(R.string.envelope_available, money.format(status.available)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.lock != null) colors.tertiary else colors.onSurfaceVariant,
            )
        }
    }
}

/** Progress towards the envelope target; nothing without a target. */
@Composable
internal fun EnvelopeProgress(status: EnvelopeStatus) {
    val progress = status.targetProgress ?: return
    val target = status.envelope.target ?: return
    val money = LocalMoneyFormatter.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.envelope_target_progress, money.format(status.balance), money.format(target)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
