/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fiscalnest.core.CashReserve
import fiscalnest.core.PiggyBankMode
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.form.CushionDraft
import io.github.skrpld.fiscalnest.domain.form.CushionField
import io.github.skrpld.fiscalnest.domain.form.CushionValues
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.PeriodDraft
import io.github.skrpld.fiscalnest.domain.form.PeriodField
import io.github.skrpld.fiscalnest.domain.form.PeriodType
import io.github.skrpld.fiscalnest.domain.form.PiggyBankDraft
import io.github.skrpld.fiscalnest.domain.form.PiggyBankField
import io.github.skrpld.fiscalnest.domain.form.PiggyBankValues
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.format.MoneyFormatter
import io.github.skrpld.fiscalnest.domain.model.Limits
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.ThemeMode
import io.github.skrpld.fiscalnest.ui.common.ChoiceChips
import io.github.skrpld.fiscalnest.ui.common.DateField
import io.github.skrpld.fiscalnest.ui.common.IntegerField
import io.github.skrpld.fiscalnest.ui.common.MoneyField
import io.github.skrpld.fiscalnest.ui.common.PercentField
import io.github.skrpld.fiscalnest.ui.common.descriptionRes
import io.github.skrpld.fiscalnest.ui.common.labelRes
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A dialog with a confirm and a cancel button around form [content].
 */
@Composable
private fun FormDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PeriodDialog(
    rule: PeriodRule,
    today: LocalDate,
    onConfirm: (PeriodRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(rule, today) { PeriodDraft.from(rule, today) }
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var startDay by rememberSaveable { mutableStateOf(initial.startDay) }
    var length by rememberSaveable { mutableStateOf(initial.lengthDays) }
    var anchorEpochDay by rememberSaveable { mutableLongStateOf(initial.anchor.toEpochDay()) }
    var errors by remember { mutableStateOf<Map<PeriodField, FieldError>>(emptyMap()) }

    FormDialog(
        title = stringResource(R.string.settings_period),
        onConfirm = {
            when (val result = PeriodDraft(type, startDay, length, LocalDate.ofEpochDay(anchorEpochDay)).validate()) {
                is Validation.Valid -> onConfirm(result.value)
                is Validation.Invalid -> errors = result.errors
            }
        },
        onDismiss = onDismiss,
    ) {
        ChoiceChips(
            options = PeriodType.entries,
            selected = type,
            onSelect = { type = it },
            label = {
                stringResource(if (it == PeriodType.MONTHLY) R.string.period_type_monthly else R.string.period_type_fixed)
            },
        )
        when (type) {
            PeriodType.MONTHLY -> {
                HintText(stringResource(R.string.period_monthly_hint))
                IntegerField(
                    value = startDay,
                    onValueChange = { startDay = it },
                    label = stringResource(R.string.period_start_day),
                    error = errors[PeriodField.START_DAY],
                    supportingText = stringResource(R.string.period_start_day_hint, Limits.MAX_MONTHLY_START_DAY),
                    imeAction = ImeAction.Done,
                )
            }
            PeriodType.FIXED_LENGTH -> {
                HintText(stringResource(R.string.period_fixed_hint))
                IntegerField(
                    value = length,
                    onValueChange = { length = it },
                    label = stringResource(R.string.period_length_days),
                    error = errors[PeriodField.LENGTH],
                    imeAction = ImeAction.Done,
                )
                DateField(
                    date = LocalDate.ofEpochDay(anchorEpochDay),
                    onDateChange = { anchorEpochDay = it.toEpochDay() },
                    label = stringResource(R.string.period_anchor),
                )
            }
        }
    }
}

@Composable
internal fun HorizonDialog(current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableFloatStateOf(current.toFloat()) }
    val periods = value.roundToInt()
    FormDialog(
        title = stringResource(R.string.settings_horizon),
        onConfirm = { onConfirm(periods) },
        onDismiss = onDismiss,
    ) {
        HintText(stringResource(R.string.settings_horizon_hint))
        Text(
            text = pluralStringResource(R.plurals.forecast_periods, periods, periods),
            style = MaterialTheme.typography.titleLarge,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = Limits.MIN_FORECAST_PERIODS.toFloat()..Limits.MAX_FORECAST_PERIODS.toFloat(),
            steps = Limits.MAX_FORECAST_PERIODS - Limits.MIN_FORECAST_PERIODS - 1,
        )
    }
}

@Composable
internal fun CurrencyDialog(
    selectedCode: String?,
    locale: Locale,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val deviceCurrency = remember(locale) { MoneyFormatter.localeCurrency(locale) }
    val codes = remember(deviceCurrency) {
        (listOf(deviceCurrency.currencyCode) + MoneyFormatter.COMMON_CURRENCIES).distinct()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_currency)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ChoiceRow(
                    selected = selectedCode == null,
                    text = stringResource(R.string.settings_currency_device, deviceCurrency.currencyCode),
                    supportingText = deviceCurrency.getDisplayName(locale),
                    onClick = { onSelect(null) },
                )
                codes.forEach { code ->
                    val currency = Currency.getInstance(code)
                    ChoiceRow(
                        selected = selectedCode == code,
                        text = "$code · ${currency.getSymbol(locale)}",
                        supportingText = currency.getDisplayName(locale),
                        onClick = { onSelect(code) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
internal fun CushionDialog(
    current: BigDecimal,
    target: BigDecimal,
    onConfirm: (CushionValues) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(current, target) { CushionDraft.from(current, target) }
    var currentText by rememberSaveable { mutableStateOf(initial.current) }
    var targetText by rememberSaveable { mutableStateOf(initial.target) }
    var errors by remember { mutableStateOf<Map<CushionField, FieldError>>(emptyMap()) }
    FormDialog(
        title = stringResource(R.string.settings_cushion),
        onConfirm = {
            when (val result = CushionDraft(currentText, targetText).validate()) {
                is Validation.Valid -> onConfirm(result.value)
                is Validation.Invalid -> errors = result.errors
            }
        },
        onDismiss = onDismiss,
    ) {
        HintText(stringResource(R.string.settings_cushion_hint))
        MoneyField(
            value = currentText,
            onValueChange = { currentText = it },
            label = stringResource(R.string.field_cushion_current),
            error = errors[CushionField.CURRENT],
        )
        MoneyField(
            value = targetText,
            onValueChange = { targetText = it },
            label = stringResource(R.string.field_cushion_target),
            error = errors[CushionField.TARGET],
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
internal fun PiggyBankDialog(
    mode: PiggyBankMode,
    target: BigDecimal,
    admissibility: BigDecimal,
    onConfirm: (PiggyBankValues) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(mode, target, admissibility) { PiggyBankDraft.from(mode, target, admissibility) }
    var selectedMode by rememberSaveable { mutableStateOf(initial.mode) }
    var targetText by rememberSaveable { mutableStateOf(initial.target) }
    var admissibilityText by rememberSaveable { mutableStateOf(initial.admissibilityPercent) }
    var errors by remember { mutableStateOf<Map<PiggyBankField, FieldError>>(emptyMap()) }
    FormDialog(
        title = stringResource(R.string.settings_piggy_bank),
        onConfirm = {
            when (val result = PiggyBankDraft(selectedMode, targetText, admissibilityText).validate()) {
                is Validation.Valid -> onConfirm(result.value)
                is Validation.Invalid -> errors = result.errors
            }
        },
        onDismiss = onDismiss,
    ) {
        HintText(stringResource(R.string.settings_piggy_hint))
        ChoiceChips(
            options = PiggyBankMode.entries,
            selected = selectedMode,
            onSelect = {
                if (it != selectedMode) targetText = ""
                selectedMode = it
            },
            label = {
                stringResource(
                    if (it == PiggyBankMode.PERCENT_OF_REMAINDER) R.string.piggy_mode_percent else R.string.piggy_mode_fixed,
                )
            },
        )
        if (selectedMode == PiggyBankMode.PERCENT_OF_REMAINDER) {
            PercentField(
                value = targetText,
                onValueChange = { targetText = it },
                label = stringResource(R.string.piggy_target_percent),
                error = errors[PiggyBankField.TARGET],
            )
        } else {
            MoneyField(
                value = targetText,
                onValueChange = { targetText = it },
                label = stringResource(R.string.piggy_target_amount),
                error = errors[PiggyBankField.TARGET],
            )
        }
        PercentField(
            value = admissibilityText,
            onValueChange = { admissibilityText = it },
            label = stringResource(R.string.piggy_admissibility),
            supportingText = stringResource(R.string.piggy_admissibility_hint),
            error = errors[PiggyBankField.ADMISSIBILITY],
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
internal fun ReservesDialog(
    selected: Set<CashReserve>,
    onToggle: (CashReserve, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reserves)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HintText(stringResource(R.string.settings_reserves_hint))
                CashReserve.entries.forEach { reserve ->
                    val checked = reserve in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(reserve, it) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(reserve.labelRes()), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(reserve.descriptionRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
internal fun ThemeDialog(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(
                        selected = mode == selected,
                        text = stringResource(mode.labelRes()),
                        onClick = { onSelect(mode) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    supportingText: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
