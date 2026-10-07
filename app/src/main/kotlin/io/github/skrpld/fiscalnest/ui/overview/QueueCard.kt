/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeStatus
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.OccurrenceDraft
import io.github.skrpld.fiscalnest.domain.form.OccurrenceField
import io.github.skrpld.fiscalnest.domain.form.OccurrenceValues
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.queue.PendingOccurrence
import io.github.skrpld.fiscalnest.ui.common.LocalDateTexts
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.MoneyField
import io.github.skrpld.fiscalnest.ui.common.SectionCard
import io.github.skrpld.fiscalnest.ui.common.labelRes
import io.github.skrpld.fiscalnest.ui.common.tabular
import io.github.skrpld.fiscalnest.ui.envelopes.EnvelopePicker
import io.github.skrpld.fiscalnest.ui.envelopes.defaultEnvelopeId
import kotlinx.coroutines.launch

/**
 * Due income and expenses the user still has to confirm. The app is not connected to a bank, so
 * money only moves in the envelopes once the user says it did.
 */
@Composable
internal fun QueueCard(
    pending: List<PendingOccurrence>,
    envelopes: List<EnvelopeStatus>,
    onConfirm: (PendingOccurrence) -> Unit,
    onSkip: (PendingOccurrence) -> Unit,
) {
    val money = LocalMoneyFormatter.current
    val dates = LocalDateTexts.current
    val envelopeNames = envelopes.associate { it.envelope.id to it.envelope.name }
    SectionCard(
        title = stringResource(R.string.queue_title),
        icon = Icons.Rounded.PendingActions,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = pluralStringResource(R.plurals.queue_hint, pending.size, pending.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        pending.forEachIndexed { index, occurrence ->
            if (index > 0) HorizontalDivider()
            val event = occurrence.event
            val isIncome = event.kind == EventKind.INCOME
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = event.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val envelope = event.envelopeId?.let(envelopeNames::get) ?: stringResource(R.string.queue_no_envelope)
                    Text(
                        text = listOf(
                            dates.dayMonth(occurrence.date),
                            stringResource(event.kind.labelRes()),
                            envelope,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = (if (isIncome) "+" else "−") + money.format(event.amount),
                    style = MaterialTheme.typography.titleSmall.tabular,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = { onSkip(occurrence) }) { Text(stringResource(R.string.queue_skip)) }
                FilledTonalButton(onClick = { onConfirm(occurrence) }) {
                    Text(stringResource(if (isIncome) R.string.queue_confirm_income else R.string.queue_confirm_expense))
                }
            }
        }
    }
}

/**
 * Bottom sheet that confirms [occurrence]: the actual amount, which may differ from the plan, and
 * the envelope the money arrives in or is paid from.
 *
 * @param onSubmit moves the money and returns the validation result
 */
@Composable
internal fun ConfirmOccurrenceSheet(
    occurrence: PendingOccurrence,
    envelopes: List<EnvelopeStatus>,
    onSubmit: (OccurrenceDraft) -> Validation<OccurrenceValues, OccurrenceField>,
    onCreateEnvelope: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val event = occurrence.event
    val isIncome = event.kind == EventKind.INCOME
    val initial = OccurrenceDraft.from(event)
    var amount by rememberSaveable { mutableStateOf(initial.amount) }
    var envelopeId by rememberSaveable {
        mutableStateOf(initial.envelopeId?.takeIf { id -> envelopes.any { it.envelope.id == id } } ?: envelopes.defaultEnvelopeId())
    }
    var errors by remember { mutableStateOf<Map<OccurrenceField, FieldError>>(emptyMap()) }

    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    val submit: () -> Unit = {
        when (val result = onSubmit(OccurrenceDraft(amount, envelopeId))) {
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
                text = stringResource(if (isIncome) R.string.queue_sheet_income_title else R.string.queue_sheet_expense_title, event.name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.queue_sheet_hint, LocalDateTexts.current.date(occurrence.date)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyField(
                value = amount,
                onValueChange = {
                    amount = it
                    errors = errors - OccurrenceField.AMOUNT
                },
                label = stringResource(R.string.queue_sheet_amount),
                error = errors[OccurrenceField.AMOUNT],
            )
            EnvelopePicker(
                envelopes = envelopes,
                selectedId = envelopeId,
                onSelect = {
                    envelopeId = it
                    errors = errors - OccurrenceField.ENVELOPE - OccurrenceField.AMOUNT
                },
                label = stringResource(if (isIncome) R.string.queue_sheet_income_envelope else R.string.spending_paid_from),
                error = errors[OccurrenceField.ENVELOPE],
                onCreateEnvelope = onCreateEnvelope,
                showAvailable = !isIncome,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = close) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = submit, enabled = envelopes.isNotEmpty()) {
                    Text(stringResource(if (isIncome) R.string.queue_confirm_income else R.string.queue_confirm_expense))
                }
            }
        }
    }
}
