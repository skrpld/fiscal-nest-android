/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.model.BudgetEvent
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.ui.common.EmptyState
import io.github.skrpld.fiscalnest.ui.common.FabClearance
import io.github.skrpld.fiscalnest.ui.common.IconBadge
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.LocalDateTexts
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.groupTitleRes
import io.github.skrpld.fiscalnest.ui.common.icon
import io.github.skrpld.fiscalnest.ui.common.recurrenceText
import io.github.skrpld.fiscalnest.ui.common.tabular
import io.github.skrpld.fiscalnest.ui.common.withContentPadding
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Events of one kind, in the order the user added them. */
data class EventGroup(val kind: EventKind, val events: List<BudgetEvent>)

/**
 * State of the events tab.
 */
sealed interface EventsUiState {
    data object Loading : EventsUiState

    /** @property groups non-empty groups in the order income, mandatory, optional */
    data class Ready(val today: LocalDate, val groups: List<EventGroup>) : EventsUiState
}

class EventsViewModel(repository: BudgetRepository, dateProvider: DateProvider) : ViewModel() {
    val uiState: StateFlow<EventsUiState> = repository.data
        .map { data ->
            EventsUiState.Ready(
                today = dateProvider.today(),
                groups = EventKind.entries.mapNotNull { kind ->
                    data.events.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { EventGroup(kind, it) }
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState.Loading)
}

@Composable
fun EventsRoute(
    onAddEvent: (EventKind?) -> Unit,
    onOpenEvent: (String) -> Unit,
    viewModel: EventsViewModel = appViewModel { EventsViewModel(it.repository, it.dateProvider) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EventsScreen(state, onAddEvent, onOpenEvent)
}

@Composable
fun EventsScreen(
    state: EventsUiState,
    onAddEvent: (EventKind?) -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.events_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddEvent(null) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_event)) },
            )
        },
    ) { padding ->
        when (state) {
            EventsUiState.Loading -> LoadingContent(Modifier.padding(padding))
            is EventsUiState.Ready -> if (state.groups.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Event,
                    title = stringResource(R.string.events_empty_title),
                    text = stringResource(R.string.events_empty_text),
                    modifier = Modifier.padding(padding),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onAddEvent(EventKind.INCOME) }) {
                            Text(stringResource(R.string.action_add_income))
                        }
                        FilledTonalButton(onClick = { onAddEvent(EventKind.MANDATORY_EXPENSE) }) {
                            Text(stringResource(R.string.action_add_expense))
                        }
                    }
                }
            } else {
                EventList(state, padding, onOpenEvent)
            }
        }
    }
}

@Composable
private fun EventList(
    state: EventsUiState.Ready,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onOpenEvent: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding.withContentPadding(extraBottom = FabClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.groups, key = { it.kind.name }) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(group.kind.groupTitleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    group.events.forEach { event ->
                        EventItem(event, state.today, onClick = { onOpenEvent(event.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EventItem(event: BudgetEvent, today: LocalDate, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val dates = LocalDateTexts.current
    val colors = MaterialTheme.colorScheme
    val ended = event.hasEndedBefore(today)
    val (container, content) = when (event.kind) {
        EventKind.INCOME -> colors.primaryContainer to colors.onPrimaryContainer
        EventKind.MANDATORY_EXPENSE -> colors.tertiaryContainer to colors.onTertiaryContainer
        EventKind.OPTIONAL_EXPENSE -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    val schedule = recurrenceText(event.recurrence)
    val endDate = event.endDate
    val dateText = when {
        ended -> stringResource(R.string.event_ended)
        endDate != null -> stringResource(
            R.string.event_date_range,
            dates.date(event.startDate),
            dates.date(endDate),
        )
        event.startDate > today -> stringResource(R.string.event_starts_on, dates.date(event.startDate))
        else -> stringResource(R.string.event_since, dates.date(event.startDate))
    }
    ListItem(
        headlineContent = { Text(event.name) },
        supportingContent = { Text("$schedule · $dateText") },
        leadingContent = { IconBadge(event.kind.icon, container, content) },
        trailingContent = {
            Text(
                text = if (event.kind == EventKind.INCOME) money.formatSigned(event.amount) else money.format(event.amount),
                style = MaterialTheme.typography.titleSmall.tabular,
                color = if (event.kind == EventKind.INCOME) colors.primary else colors.onSurface,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .alpha(if (ended) 0.6f else 1f),
    )
}
