/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.spending

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeStatus
import io.github.skrpld.fiscalnest.domain.form.SpendingDraft
import io.github.skrpld.fiscalnest.domain.form.SpendingField
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.Spending
import io.github.skrpld.fiscalnest.domain.model.deleteSpending
import io.github.skrpld.fiscalnest.domain.model.upsertSpending
import io.github.skrpld.fiscalnest.domain.period.DateRange
import io.github.skrpld.fiscalnest.domain.period.PeriodResolver
import io.github.skrpld.fiscalnest.ui.common.EmptyState
import io.github.skrpld.fiscalnest.ui.common.FabClearance
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.LocalDateTexts
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.tabular
import io.github.skrpld.fiscalnest.ui.common.withContentPadding
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * State of the spending log.
 */
sealed interface SpendingsUiState {
    data object Loading : SpendingsUiState

    /**
     * @property currentPeriod period whose spending counts in today's budget
     * @property spendings all logged spending, newest first
     * @property envelopes every envelope, spending can be taken from any of them
     */
    data class Ready(
        val today: LocalDate,
        val currentPeriod: DateRange,
        val spendings: List<Spending>,
        val envelopes: List<EnvelopeStatus> = emptyList(),
    ) : SpendingsUiState
}

class SpendingsViewModel(
    private val repository: BudgetRepository,
    private val dateProvider: DateProvider,
    idGenerator: IdGenerator,
) : ViewModel() {
    private val recorder = SpendingRecorder(repository, idGenerator, viewModelScope)

    val uiState: StateFlow<SpendingsUiState> = repository.data
        .map { data ->
            val today = dateProvider.today()
            SpendingsUiState.Ready(
                today = today,
                currentPeriod = PeriodResolver.periodContaining(data.settings.period, today),
                spendings = data.spendings.sortedWith(compareByDescending<Spending> { it.date }),
                envelopes = EnvelopeCalculator.summary(data, today).statuses,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingsUiState.Loading)

    /** Saves new spending, or changes [existing] spending. */
    fun save(draft: SpendingDraft, existing: Spending? = null): Validation<Spending, SpendingField> =
        recorder.record(draft, dateProvider.today(), existing)

    fun delete(spending: Spending) {
        viewModelScope.launch { repository.update { it.deleteSpending(spending.id) } }
    }

    /** Puts back spending removed by [delete]. */
    fun restore(spending: Spending) {
        viewModelScope.launch { repository.update { it.upsertSpending(spending) } }
    }
}

@Composable
fun SpendingsRoute(
    onBack: () -> Unit,
    onCreateEnvelope: () -> Unit,
    viewModel: SpendingsViewModel = appViewModel { SpendingsViewModel(it.repository, it.dateProvider, it.idGenerator) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SpendingsScreen(
        state = state,
        onBack = onBack,
        onSave = viewModel::save,
        onCreateEnvelope = onCreateEnvelope,
        onDelete = viewModel::delete,
        onRestore = viewModel::restore,
    )
}

@Composable
fun SpendingsScreen(
    state: SpendingsUiState,
    onBack: () -> Unit,
    onSave: (SpendingDraft, Spending?) -> Validation<Spending, SpendingField>,
    onCreateEnvelope: () -> Unit,
    onDelete: (Spending) -> Unit,
    onRestore: (Spending) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val deletedMessage = stringResource(R.string.spending_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.spendings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_spending)) },
            )
        },
    ) { padding ->
        when (state) {
            SpendingsUiState.Loading -> LoadingContent(Modifier.padding(padding))
            is SpendingsUiState.Ready -> if (state.spendings.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Receipt,
                    title = stringResource(R.string.spendings_empty_title),
                    text = stringResource(R.string.spendings_empty_text),
                    modifier = Modifier.padding(padding),
                )
            } else {
                SpendingList(
                    state = state,
                    padding = padding,
                    onEdit = { editingId = it.id },
                    onDelete = { spending ->
                        onDelete(spending)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = deletedMessage,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) onRestore(spending)
                        }
                    },
                )
            }
        }
    }
    if (showSheet && state is SpendingsUiState.Ready) {
        SpendingSheet(
            today = state.today,
            envelopes = state.envelopes,
            onSubmit = { draft -> onSave(draft, null) },
            onCreateEnvelope = {
                showSheet = false
                onCreateEnvelope()
            },
            onDismiss = { showSheet = false },
        )
    }
    val ready = state as? SpendingsUiState.Ready
    val editing = ready?.spendings?.firstOrNull { it.id == editingId }
    if (ready != null && editing != null) {
        SpendingSheet(
            today = ready.today,
            envelopes = ready.envelopes,
            onSubmit = { draft -> onSave(draft, editing) },
            onCreateEnvelope = {
                editingId = null
                onCreateEnvelope()
            },
            onDismiss = { editingId = null },
            existing = editing,
        )
    }
}

@Composable
private fun SpendingList(
    state: SpendingsUiState.Ready,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onEdit: (Spending) -> Unit,
    onDelete: (Spending) -> Unit,
) {
    val money = LocalMoneyFormatter.current
    val dates = LocalDateTexts.current
    val noNote = stringResource(R.string.spending_no_note)
    val notCounted = stringResource(R.string.spending_not_in_current_period)
    val noEnvelope = stringResource(R.string.spending_without_envelope)
    val envelopeNames = state.envelopes.associate { it.envelope.id to it.envelope.name }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding.withContentPadding(horizontal = 0.dp, extraBottom = FabClearance),
    ) {
        items(state.spendings, key = { it.id }) { spending ->
            val inCurrentPeriod = spending.date in state.currentPeriod
            ListItem(
                headlineContent = { Text(spending.note.ifBlank { noNote }) },
                supportingContent = {
                    val parts = listOfNotNull(
                        dates.date(spending.date),
                        spending.envelopeId?.let(envelopeNames::get) ?: noEnvelope,
                        notCounted.takeUnless { inCurrentPeriod },
                    )
                    Text(parts.joinToString(" · "))
                },
                trailingContent = {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            text = money.format(spending.amount),
                            style = MaterialTheme.typography.titleSmall.tabular,
                        )
                        IconButton(onClick = { onDelete(spending) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
                modifier = Modifier
                    .animateItem()
                    .clickable { onEdit(spending) },
            )
        }
    }
}
