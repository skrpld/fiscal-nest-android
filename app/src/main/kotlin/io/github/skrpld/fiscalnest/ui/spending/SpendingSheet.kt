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
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.SpendingDraft
import io.github.skrpld.fiscalnest.domain.form.SpendingField
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.Spending
import io.github.skrpld.fiscalnest.domain.model.upsertSpending
import io.github.skrpld.fiscalnest.ui.common.DateField
import io.github.skrpld.fiscalnest.ui.common.FormTextField
import io.github.skrpld.fiscalnest.ui.common.MoneyField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Validates and stores spending entered in [AddSpendingSheet].
 */
class SpendingRecorder(
    private val repository: BudgetRepository,
    private val idGenerator: IdGenerator,
) {
    /**
     * Saves the spending of [draft] in [scope] when it is valid.
     *
     * @return the validation result, so the form can show field errors
     */
    fun record(draft: SpendingDraft, today: LocalDate, scope: CoroutineScope): Validation<Spending, SpendingField> {
        val result = draft.validate(idGenerator.newId(), today)
        if (result is Validation.Valid) {
            scope.launch { repository.update { it.upsertSpending(result.value) } }
        }
        return result
    }
}

/**
 * Bottom sheet for logging unscheduled spending.
 *
 * @param onSubmit stores the spending and returns the validation result
 */
@Composable
fun AddSpendingSheet(
    today: LocalDate,
    onSubmit: (SpendingDraft) -> Validation<Spending, SpendingField>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var errors by remember { mutableStateOf<Map<SpendingField, FieldError>>(emptyMap()) }
    val focusRequester = remember { FocusRequester() }

    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    val submit: () -> Unit = {
        when (val result = onSubmit(SpendingDraft(amount, note, LocalDate.ofEpochDay(dateEpochDay)))) {
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
            Text(stringResource(R.string.spending_sheet_title), style = MaterialTheme.typography.headlineSmall)
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
                    errors = errors - SpendingField.DATE
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
                Button(onClick = submit) { Text(stringResource(R.string.action_save)) }
            }
        }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}
