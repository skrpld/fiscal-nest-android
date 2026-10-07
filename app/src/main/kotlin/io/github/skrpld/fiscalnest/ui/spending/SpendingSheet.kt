/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.spending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeStatus
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.SpendingDraft
import io.github.skrpld.fiscalnest.domain.form.SpendingField
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.Spending
import io.github.skrpld.fiscalnest.domain.model.deleteSpending
import io.github.skrpld.fiscalnest.domain.model.upsertSpending
import io.github.skrpld.fiscalnest.ui.common.DateField
import io.github.skrpld.fiscalnest.ui.common.FormTextField
import io.github.skrpld.fiscalnest.ui.common.MoneyField
import io.github.skrpld.fiscalnest.ui.envelopes.EnvelopePicker
import io.github.skrpld.fiscalnest.ui.envelopes.defaultEnvelopeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Validates and stores spending entered in [SpendingSheet]. Spending is always taken from an
 * envelope and must fit what the envelope allows.
 *
 * @param scope scope of the owning view model; saves run in it
 */
class SpendingRecorder(
    private val repository: BudgetRepository,
    private val idGenerator: IdGenerator,
    private val scope: CoroutineScope,
) {
    // Eager, so that record() always checks the envelope against the stored operations.
    private val data: StateFlow<AppData?> = repository.data.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Saves the spending of [draft] when it is valid.
     *
     * @param existing spending being edited; its own withdrawal does not count against the envelope
     * @return the validation result, so the form can show field errors
     */
    fun record(draft: SpendingDraft, today: LocalDate, existing: Spending? = null): Validation<Spending, SpendingField> {
        val current = data.value?.let { if (existing == null) it else it.deleteSpending(existing.id) }
        val available = draft.envelopeId
            ?.let { id -> current?.let { EnvelopeCalculator.status(it, id, draft.date)?.available } }
            ?: BigDecimal.ZERO
        val result = draft.validate(existing?.id ?: idGenerator.newId(), today, available)
        if (result is Validation.Valid) {
            scope.launch { repository.update { it.upsertSpending(result.value) } }
        }
        return result
    }
}

/**
 * Bottom sheet for logging unscheduled spending, or for editing [existing] spending.
 *
 * @param envelopes envelopes the spending can be taken from
 * @param onSubmit stores the spending and returns the validation result
 * @param onCreateEnvelope opens the envelope editor; offered while there is no envelope
 */
@Composable
fun SpendingSheet(
    today: LocalDate,
    envelopes: List<EnvelopeStatus>,
    onSubmit: (SpendingDraft) -> Validation<Spending, SpendingField>,
    onCreateEnvelope: () -> Unit,
    onDismiss: () -> Unit,
    existing: Spending? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val initial = existing?.let(SpendingDraft::from)
    var amount by rememberSaveable { mutableStateOf(initial?.amount.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var dateEpochDay by rememberSaveable { mutableStateOf((initial?.date ?: today).toEpochDay()) }
    var envelopeId by rememberSaveable {
        mutableStateOf(if (existing == null) envelopes.defaultEnvelopeId() else initial?.envelopeId)
    }
    var errors by remember { mutableStateOf<Map<SpendingField, FieldError>>(emptyMap()) }
    val focusRequester = remember { FocusRequester() }

    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    val submit: () -> Unit = {
        when (val result = onSubmit(SpendingDraft(amount, note, LocalDate.ofEpochDay(dateEpochDay), envelopeId))) {
            is Validation.Valid -> close()
            is Validation.Invalid -> errors = result.errors
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(if (existing == null) R.string.spending_sheet_title else R.string.spending_sheet_edit_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.spending_sheet_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyField(
                value = amount,
                onValueChange = {
                    amount = it
                    errors = errors - SpendingField.AMOUNT
                },
                label = stringResource(R.string.field_amount),
                modifier = Modifier.focusRequester(focusRequester),
                error = errors[SpendingField.AMOUNT],
            )
            EnvelopePicker(
                envelopes = envelopes,
                selectedId = envelopeId,
                onSelect = {
                    envelopeId = it
                    errors = errors - SpendingField.ENVELOPE - SpendingField.AMOUNT
                },
                label = stringResource(R.string.spending_paid_from),
                error = errors[SpendingField.ENVELOPE],
                onCreateEnvelope = onCreateEnvelope,
            )
            FormTextField(
                value = note,
                onValueChange = {
                    note = it
                    errors = errors - SpendingField.NOTE
                },
                label = stringResource(R.string.field_note_optional),
                error = errors[SpendingField.NOTE],
                imeAction = ImeAction.Done,
            )
            DateField(
                date = LocalDate.ofEpochDay(dateEpochDay),
                onDateChange = {
                    dateEpochDay = it.toEpochDay()
                    errors = errors - SpendingField.DATE - SpendingField.AMOUNT
                },
                label = stringResource(R.string.field_date),
                error = errors[SpendingField.DATE],
                latest = today,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = close) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = submit, enabled = envelopes.isNotEmpty()) { Text(stringResource(R.string.action_save)) }
            }
        }
        LaunchedEffect(Unit) {
            if (envelopes.isNotEmpty()) focusRequester.requestFocus()
        }
    }
}
