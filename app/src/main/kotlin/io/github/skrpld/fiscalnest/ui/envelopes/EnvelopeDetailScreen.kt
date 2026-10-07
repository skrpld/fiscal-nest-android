/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.envelopes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeStatus
import io.github.skrpld.fiscalnest.domain.form.EnvelopeOperationDraft
import io.github.skrpld.fiscalnest.domain.form.EnvelopeOperationField
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopePolicy
import io.github.skrpld.fiscalnest.domain.model.deleteEnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.upsertEnvelopeOperation
import io.github.skrpld.fiscalnest.ui.common.EmptyState
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.LocalDateTexts
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.tabular
import io.github.skrpld.fiscalnest.ui.common.withContentPadding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * State of the envelope details.
 */
sealed interface EnvelopeDetailUiState {
    data object Loading : EnvelopeDetailUiState

    /** The envelope has been deleted. */
    data object Missing : EnvelopeDetailUiState

    /** @property operations operations of the envelope, newest first */
    data class Ready(
        val today: LocalDate,
        val status: EnvelopeStatus,
        val operations: List<EnvelopeOperation>,
    ) : EnvelopeDetailUiState
}

class EnvelopeDetailViewModel(
    private val repository: BudgetRepository,
    private val dateProvider: DateProvider,
    private val idGenerator: IdGenerator,
    private val envelopeId: String,
) : ViewModel() {
    private val today = MutableStateFlow(dateProvider.today())

    // Eager, so that record() always checks the policy against the stored operations.
    private val data: StateFlow<AppData?> = repository.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<EnvelopeDetailUiState> = combine(repository.data, today) { data, date ->
        val status = EnvelopeCalculator.status(data, envelopeId, date)
        if (status == null) {
            EnvelopeDetailUiState.Missing
        } else {
            EnvelopeDetailUiState.Ready(
                today = date,
                status = status,
                operations = data.envelopeOperations
                    .filter { it.envelopeId == envelopeId }
                    .sortedWith(compareByDescending<EnvelopeOperation> { it.date }),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnvelopeDetailUiState.Loading)

    /** Re-reads the date, so locks and period limits move on when the app is resumed on a new day. */
    fun refreshDate() {
        today.value = dateProvider.today()
    }

    /**
     * Saves a deposit or a withdrawal when it is valid; a withdrawal must fit what the envelope
     * policy allows on the operation date.
     *
     * @return the validation result, so the form can show field errors
     */
    fun record(type: EnvelopeOperationType, draft: EnvelopeOperationDraft): Validation<EnvelopeOperation, EnvelopeOperationField> {
        val current = data.value ?: return Validation.Invalid(emptyMap())
        val available = EnvelopeCalculator.status(current, envelopeId, draft.date)?.available ?: BigDecimal.ZERO
        val result = draft.validate(idGenerator.newId(), envelopeId, type, today.value, available)
        if (result is Validation.Valid) {
            viewModelScope.launch { repository.update { it.upsertEnvelopeOperation(result.value) } }
        }
        return result
    }

    fun delete(operation: EnvelopeOperation) {
        viewModelScope.launch { repository.update { it.deleteEnvelopeOperation(operation.id) } }
    }

    /** Puts back an operation removed by [delete]. */
    fun restore(operation: EnvelopeOperation) {
        viewModelScope.launch { repository.update { it.upsertEnvelopeOperation(operation) } }
    }
}

@Composable
fun EnvelopeDetailRoute(
    envelopeId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: EnvelopeDetailViewModel = appViewModel(key = "envelope-$envelopeId") {
        EnvelopeDetailViewModel(it.repository, it.dateProvider, it.idGenerator, envelopeId)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDate() }
    EnvelopeDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onRecord = viewModel::record,
        onDelete = viewModel::delete,
        onRestore = viewModel::restore,
    )
}

@Composable
fun EnvelopeDetailScreen(
    state: EnvelopeDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRecord: (EnvelopeOperationType, EnvelopeOperationDraft) -> Validation<EnvelopeOperation, EnvelopeOperationField>,
    onDelete: (EnvelopeOperation) -> Unit,
    onRestore: (EnvelopeOperation) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheetType by rememberSaveable { mutableStateOf<EnvelopeOperationType?>(null) }
    val deletedMessage = stringResource(R.string.envelope_operation_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = (state as? EnvelopeDetailUiState.Ready)?.status?.envelope?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state is EnvelopeDetailUiState.Ready) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            EnvelopeDetailUiState.Loading -> LoadingContent(Modifier.padding(padding))
            EnvelopeDetailUiState.Missing -> EmptyState(
                icon = Icons.Rounded.Delete,
                title = stringResource(R.string.envelope_missing_title),
                text = stringResource(R.string.envelope_missing_text),
                modifier = Modifier.padding(padding),
            )
            is EnvelopeDetailUiState.Ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EnvelopeDetailContent(
                    state = state,
                    padding = padding,
                    onOpenSheet = { sheetType = it },
                    onDelete = { operation ->
                        onDelete(operation)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = deletedMessage,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) onRestore(operation)
                        }
                    },
                )
            }
        }
    }
    val type = sheetType
    if (type != null && state is EnvelopeDetailUiState.Ready) {
        val money = LocalMoneyFormatter.current
        val name = state.status.envelope.name
        EnvelopeOperationSheet(
            title = when (type) {
                EnvelopeOperationType.DEPOSIT -> stringResource(R.string.envelope_deposit_title, name)
                EnvelopeOperationType.WITHDRAWAL -> stringResource(R.string.envelope_withdraw_title, name)
            },
            hint = when (type) {
                EnvelopeOperationType.DEPOSIT -> stringResource(R.string.envelope_deposit_hint)
                EnvelopeOperationType.WITHDRAWAL ->
                    stringResource(R.string.envelope_withdraw_hint, money.format(state.status.available))
            },
            today = state.today,
            onSubmit = { draft -> onRecord(type, draft) },
            onDismiss = { sheetType = null },
        )
    }
}

@Composable
private fun EnvelopeDetailContent(
    state: EnvelopeDetailUiState.Ready,
    padding: PaddingValues,
    onOpenSheet: (EnvelopeOperationType) -> Unit,
    onDelete: (EnvelopeOperation) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxSize(),
        contentPadding = padding.withContentPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            SummaryCard(state.status, onOpenSheet)
        }
        item(key = "history") {
            Text(
                text = stringResource(R.string.envelope_history),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp),
            )
        }
        if (state.operations.isEmpty()) {
            item(key = "history-empty") {
                Text(
                    text = stringResource(R.string.envelope_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        items(state.operations, key = { it.id }) { operation ->
            OperationItem(operation, onDelete = { onDelete(operation) }, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun SummaryCard(status: EnvelopeStatus, onOpenSheet: (EnvelopeOperationType) -> Unit) {
    val money = LocalMoneyFormatter.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.envelope_balance), style = MaterialTheme.typography.titleMedium)
            Text(
                text = money.format(status.balance),
                style = MaterialTheme.typography.displaySmall.tabular,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(status.envelope.policy.icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(envelopePolicyText(status.envelope), style = MaterialTheme.typography.bodyMedium)
            }
            val policy = status.envelope.policy
            if (policy is EnvelopePolicy.PeriodLimit) {
                Text(
                    text = stringResource(
                        R.string.envelope_period_withdrawn,
                        money.format(status.withdrawnInPeriod),
                        money.format(policy.limit),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EnvelopeProgress(status)
            Text(
                text = envelopeLockText(status.lock)
                    ?: stringResource(R.string.envelope_available, money.format(status.available)),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onOpenSheet(EnvelopeOperationType.DEPOSIT) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_deposit))
                }
                FilledTonalButton(
                    onClick = { onOpenSheet(EnvelopeOperationType.WITHDRAWAL) },
                    enabled = status.available.signum() > 0,
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_withdraw))
                }
            }
        }
    }
}

@Composable
private fun OperationItem(operation: EnvelopeOperation, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    val dates = LocalDateTexts.current
    val isDeposit = operation.type == EnvelopeOperationType.DEPOSIT
    val typeLabel = stringResource(if (isDeposit) R.string.envelope_operation_deposit else R.string.envelope_operation_withdrawal)
    ListItem(
        headlineContent = { Text(operation.note.ifBlank { typeLabel }) },
        supportingContent = {
            Text(if (operation.note.isBlank()) dates.date(operation.date) else "${dates.date(operation.date)} · $typeLabel")
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isDeposit) money.formatSigned(operation.amount) else money.format(operation.amount.negate()),
                    style = MaterialTheme.typography.titleSmall.tabular,
                    color = if (isDeposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
        modifier = modifier,
    )
}
