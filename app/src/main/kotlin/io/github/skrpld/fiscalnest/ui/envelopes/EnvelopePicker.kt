/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.envelopes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeStatus
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.model.EnvelopeRole
import io.github.skrpld.fiscalnest.ui.common.ChoiceChips
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.fieldErrorText

/**
 * Required choice of the envelope money moves through. Money is always kept in envelopes, so
 * without any envelope it offers to create one instead.
 *
 * @param showAvailable show what the selected envelope allows to withdraw
 */
@Composable
fun EnvelopePicker(
    envelopes: List<EnvelopeStatus>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    label: String,
    error: FieldError?,
    onCreateEnvelope: () -> Unit,
    modifier: Modifier = Modifier,
    showAvailable: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (envelopes.isEmpty()) {
            Text(
                text = stringResource(R.string.envelope_picker_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onCreateEnvelope) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_envelope))
            }
        } else {
            ChoiceChips<String?>(
                options = envelopes.map { it.envelope.id },
                selected = selectedId,
                onSelect = { id -> if (id != null) onSelect(id) },
                label = { id -> envelopes.firstOrNull { it.envelope.id == id }?.envelope?.name.orEmpty() },
            )
            val selected = envelopes.firstOrNull { it.envelope.id == selectedId }
            if (showAvailable && selected != null) {
                Text(
                    text = stringResource(
                        R.string.spending_envelope_available,
                        LocalMoneyFormatter.current.format(selected.available),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (error != null) {
            Text(
                text = fieldErrorText(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The envelope preselected for new spending or a new event: the first one for everyday spending,
 * otherwise the first one.
 */
fun List<EnvelopeStatus>.defaultEnvelopeId(): String? =
    (firstOrNull { it.envelope.role == EnvelopeRole.SPENDING } ?: firstOrNull())?.envelope?.id
