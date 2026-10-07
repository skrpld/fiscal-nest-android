/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeStatus
import io.github.skrpld.fiscalnest.domain.form.EventDraft
import io.github.skrpld.fiscalnest.domain.form.EventField
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.RecurrenceType
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.ui.common.ChoiceChips
import io.github.skrpld.fiscalnest.ui.common.ConfirmDialog
import io.github.skrpld.fiscalnest.ui.common.DateField
import io.github.skrpld.fiscalnest.ui.common.FormTextField
import io.github.skrpld.fiscalnest.ui.common.IntegerField
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.MoneyField
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.labelRes
import io.github.skrpld.fiscalnest.ui.envelopes.EnvelopePicker

@Composable
fun EventEditorRoute(
    eventId: String?,
    initialKind: EventKind?,
    onClose: () -> Unit,
    onCreateEnvelope: () -> Unit,
    viewModel: EventEditorViewModel = appViewModel(key = "event-editor-$eventId") {
        EventEditorViewModel(it.repository, it.dateProvider, it.idGenerator, eventId, initialKind)
    },
) {
    val state = viewModel.uiState
    LaunchedEffect(state.isDone) {
        if (state.isDone) onClose()
    }
    EventEditorScreen(
        state = state,
        onDraftChange = viewModel::onDraftChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onClose = onClose,
        onCreateEnvelope = onCreateEnvelope,
    )
}

@Composable
fun EventEditorScreen(
    state: EventEditorUiState,
    onDraftChange: (EventDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onCreateEnvelope: () -> Unit,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.event_editor_new else R.string.event_editor_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    Button(onClick = onSave, enabled = draft != null, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (draft == null) {
            LoadingContent(Modifier.padding(padding))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                EventForm(
                    draft = draft,
                    errors = state.errors,
                    envelopes = state.envelopes,
                    onDraftChange = onDraftChange,
                    onCreateEnvelope = onCreateEnvelope,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }
    if (showDeleteDialog && draft != null) {
        ConfirmDialog(
            title = stringResource(R.string.event_delete_title, draft.name),
            text = stringResource(R.string.event_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            icon = Icons.Rounded.Delete,
            destructive = true,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun EventForm(
    draft: EventDraft,
    errors: Map<EventField, FieldError>,
    envelopes: List<EnvelopeStatus>,
    onDraftChange: (EventDraft) -> Unit,
    onCreateEnvelope: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = stringResource(R.string.field_name),
            error = errors[EventField.NAME],
        )
        FormLabel(stringResource(R.string.event_field_kind))
        ChoiceChips(
            options = EventKind.entries,
            selected = draft.kind,
            onSelect = { onDraftChange(draft.copy(kind = it)) },
            label = { stringResource(it.labelRes()) },
        )
        MoneyField(
            value = draft.amount,
            onValueChange = { onDraftChange(draft.copy(amount = it)) },
            label = stringResource(R.string.event_field_amount),
            error = errors[EventField.AMOUNT],
        )
        EnvelopePicker(
            envelopes = envelopes,
            selectedId = draft.envelopeId,
            onSelect = { onDraftChange(draft.copy(envelopeId = it)) },
            label = stringResource(
                if (draft.kind == EventKind.INCOME) R.string.event_field_envelope_income else R.string.event_field_envelope_expense,
            ),
            error = errors[EventField.ENVELOPE],
            onCreateEnvelope = onCreateEnvelope,
            showAvailable = false,
        )
        FormLabel(stringResource(R.string.event_field_repeat))
        ChoiceChips(
            options = RecurrenceType.entries,
            selected = draft.recurrenceType,
            onSelect = { onDraftChange(draft.copy(recurrenceType = it)) },
            label = {
                stringResource(
                    when (it) {
                        RecurrenceType.ONCE -> R.string.recurrence_type_once
                        RecurrenceType.DAYS -> R.string.recurrence_type_days
                        RecurrenceType.MONTHS -> R.string.recurrence_type_months
                    },
                )
            },
        )
        when (draft.recurrenceType) {
            RecurrenceType.ONCE -> Unit
            RecurrenceType.DAYS -> IntegerField(
                value = draft.interval,
                onValueChange = { onDraftChange(draft.copy(interval = it)) },
                label = stringResource(R.string.event_field_interval_days),
                error = errors[EventField.INTERVAL],
            )
            RecurrenceType.MONTHS -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntegerField(
                    value = draft.interval,
                    onValueChange = { onDraftChange(draft.copy(interval = it)) },
                    label = stringResource(R.string.event_field_interval_months),
                    error = errors[EventField.INTERVAL],
                    modifier = Modifier.weight(1f),
                )
                IntegerField(
                    value = draft.dayOfMonth,
                    onValueChange = { onDraftChange(draft.copy(dayOfMonth = it)) },
                    label = stringResource(R.string.event_field_day_of_month),
                    error = errors[EventField.DAY_OF_MONTH],
                    supportingText = stringResource(R.string.event_field_day_of_month_hint),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        DateField(
            date = draft.startDate,
            onDateChange = { date ->
                val followsStart = draft.dayOfMonth == draft.startDate.dayOfMonth.toString()
                onDraftChange(
                    draft.copy(
                        startDate = date,
                        dayOfMonth = if (followsStart) date.dayOfMonth.toString() else draft.dayOfMonth,
                    ),
                )
            },
            label = stringResource(
                if (draft.recurrenceType == RecurrenceType.ONCE) R.string.event_field_date else R.string.event_field_start,
            ),
        )
        if (draft.recurrenceType != RecurrenceType.ONCE) {
            val endDate = draft.endDate
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.event_field_has_end), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.event_field_has_end_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = endDate != null,
                    onCheckedChange = { enabled ->
                        onDraftChange(draft.copy(endDate = if (enabled) draft.startDate.plusYears(1) else null))
                    },
                )
            }
            if (endDate != null) {
                DateField(
                    date = endDate,
                    onDateChange = { onDraftChange(draft.copy(endDate = it)) },
                    label = stringResource(R.string.event_field_end),
                    error = errors[EventField.END_DATE],
                )
            }
        }
    }
}

@Composable
internal fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
