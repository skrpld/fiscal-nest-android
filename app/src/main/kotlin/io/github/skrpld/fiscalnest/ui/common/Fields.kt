/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.form.FieldError
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/** The UTC midnight of this date, as the Material date picker expects. */
fun LocalDate.toPickerMillis(): Long = toEpochDay() * MILLIS_PER_DAY

/** The date of a Material date picker selection. */
fun pickerMillisToDate(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, MILLIS_PER_DAY))

/**
 * A text field for amounts: decimal keyboard and the currency symbol as suffix.
 */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: FieldError? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    NumberTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        suffix = LocalMoneyFormatter.current.symbol,
        keyboardType = KeyboardType.Decimal,
        modifier = modifier,
        error = error,
        supportingText = supportingText,
        imeAction = imeAction,
    )
}

/**
 * A text field for percentages on the `0..100` scale.
 */
@Composable
fun PercentField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: FieldError? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    NumberTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        suffix = "%",
        keyboardType = KeyboardType.Decimal,
        modifier = modifier,
        error = error,
        supportingText = supportingText,
        imeAction = imeAction,
    )
}

/**
 * A text field for whole numbers.
 */
@Composable
fun IntegerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: FieldError? = null,
    supportingText: String? = null,
    suffix: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    NumberTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }) },
        label = label,
        suffix = suffix,
        keyboardType = KeyboardType.Number,
        modifier = modifier,
        error = error,
        supportingText = supportingText,
        imeAction = imeAction,
    )
}

@Composable
private fun NumberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String?,
    keyboardType: KeyboardType,
    modifier: Modifier,
    error: FieldError?,
    supportingText: String?,
    imeAction: ImeAction,
) {
    val message = error?.let { fieldErrorText(it) } ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else {
            null
        },
        supportingText = if (message != null) {
            { Text(message) }
        } else {
            null
        },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
    )
}

/**
 * A plain single-line text field with error support.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: FieldError? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    val message = error?.let { fieldErrorText(it) } ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = if (message != null) {
            { Text(message) }
        } else {
            null
        },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
    )
}

/**
 * A read-only field that opens a Material date picker when tapped.
 *
 * @param latest last selectable date, or `null` for no limit
 */
@Composable
fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: FieldError? = null,
    latest: LocalDate? = null,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) showPicker = true
        }
    }
    OutlinedTextField(
        value = LocalDateTexts.current.date(date),
        onValueChange = {},
        modifier = modifier.fillMaxWidth(),
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Rounded.Event, contentDescription = stringResource(R.string.action_pick_date))
            }
        },
        supportingText = if (error != null) {
            { Text(fieldErrorText(error)) }
        } else {
            null
        },
        isError = error != null,
        singleLine = true,
        interactionSource = interactionSource,
    )
    if (showPicker) {
        DatePickerModal(
            initial = date,
            latest = latest,
            onConfirm = { selected ->
                showPicker = false
                onDateChange(selected)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun DatePickerModal(
    initial: LocalDate,
    latest: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestMillis = latest?.toPickerMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                latestMillis == null || utcTimeMillis <= latestMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(pickerMillisToDate(it)) } ?: onDismiss() },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
