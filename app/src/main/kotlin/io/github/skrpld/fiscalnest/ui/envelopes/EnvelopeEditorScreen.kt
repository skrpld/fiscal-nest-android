/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.envelopes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.form.EnvelopeDraft
import io.github.skrpld.fiscalnest.domain.form.EnvelopeField
import io.github.skrpld.fiscalnest.domain.form.EnvelopePolicyType
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopeRole
import io.github.skrpld.fiscalnest.domain.model.Limits
import io.github.skrpld.fiscalnest.domain.model.deleteEnvelope
import io.github.skrpld.fiscalnest.domain.model.upsertEnvelope
import io.github.skrpld.fiscalnest.domain.model.upsertEnvelopeOperation
import io.github.skrpld.fiscalnest.ui.common.ChoiceChips
import io.github.skrpld.fiscalnest.ui.common.ConfirmDialog
import io.github.skrpld.fiscalnest.ui.common.DateField
import io.github.skrpld.fiscalnest.ui.common.FormTextField
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.MoneyField
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.events.FormLabel
import io.github.skrpld.fiscalnest.ui.settings.HintText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Why the envelope editor closes. */
enum class EnvelopeEditorResult { SAVED, DELETED }

/**
 * State of the envelope editor.
 *
 * @property draft the form, `null` while an existing envelope loads
 * @property errors invalid fields; shown after the first save attempt
 * @property result set once the editor should close
 */
data class EnvelopeEditorUiState(
    val isNew: Boolean,
    val draft: EnvelopeDraft?,
    val errors: Map<EnvelopeField, FieldError> = emptyMap(),
    val result: EnvelopeEditorResult? = null,
)

class EnvelopeEditorViewModel(
    private val repository: BudgetRepository,
    private val dateProvider: DateProvider,
    private val idGenerator: IdGenerator,
    private val envelopeId: String?,
) : ViewModel() {
    /**
     * Held in Compose state rather than a flow, so text fields update synchronously while typing.
     */
    var uiState by mutableStateOf(
        EnvelopeEditorUiState(
            isNew = envelopeId == null,
            draft = if (envelopeId == null) EnvelopeDraft.new(dateProvider.today()) else null,
        ),
    )
        private set

    private var validateOnChange = false

    init {
        if (envelopeId != null) {
            viewModelScope.launch {
                val envelope = repository.data.first().envelopes.firstOrNull { it.id == envelopeId }
                uiState = if (envelope == null) {
                    uiState.copy(result = EnvelopeEditorResult.SAVED)
                } else {
                    uiState.copy(draft = EnvelopeDraft.from(envelope, dateProvider.today()))
                }
            }
        }
    }

    fun onDraftChange(draft: EnvelopeDraft) {
        uiState = uiState.copy(draft = draft, errors = if (validateOnChange) errorsOf(draft) else emptyMap())
    }

    /**
     * Saves the envelope; a new envelope also gets its initial balance as a first deposit.
     *
     * @param initialNote note of that deposit, in the user's language
     */
    fun save(initialNote: String) {
        val draft = uiState.draft ?: return
        when (val result = draft.validate(envelopeId ?: idGenerator.newId())) {
            is Validation.Valid -> viewModelScope.launch {
                val values = result.value
                val initialDeposit = if (envelopeId == null && values.initialBalance.signum() > 0) {
                    EnvelopeOperation(
                        id = idGenerator.newId(),
                        envelopeId = values.envelope.id,
                        type = EnvelopeOperationType.DEPOSIT,
                        amount = values.initialBalance,
                        date = dateProvider.today(),
                        note = initialNote.take(Limits.MAX_NOTE_LENGTH),
                    )
                } else {
                    null
                }
                repository.update { data ->
                    val saved = data.upsertEnvelope(values.envelope)
                    if (initialDeposit != null) saved.upsertEnvelopeOperation(initialDeposit) else saved
                }
                uiState = uiState.copy(result = EnvelopeEditorResult.SAVED)
            }
            is Validation.Invalid -> {
                validateOnChange = true
                uiState = uiState.copy(errors = result.errors)
            }
        }
    }

    /** Deletes the envelope and its history. */
    fun delete() {
        val id = envelopeId ?: return
        viewModelScope.launch {
            repository.update { it.deleteEnvelope(id) }
            uiState = uiState.copy(result = EnvelopeEditorResult.DELETED)
        }
    }

    private fun errorsOf(draft: EnvelopeDraft): Map<EnvelopeField, FieldError> =
        (draft.validate(envelopeId ?: "draft") as? Validation.Invalid)?.errors.orEmpty()
}

/**
 * @param onClose called after saving or cancelling
 * @param onDeleted called after the envelope has been deleted
 */
@Composable
fun EnvelopeEditorRoute(
    envelopeId: String?,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: EnvelopeEditorViewModel = appViewModel(key = "envelope-editor-$envelopeId") {
        EnvelopeEditorViewModel(it.repository, it.dateProvider, it.idGenerator, envelopeId)
    },
) {
    val state = viewModel.uiState
    LaunchedEffect(state.result) {
        when (state.result) {
            null -> Unit
            EnvelopeEditorResult.SAVED -> onClose()
            EnvelopeEditorResult.DELETED -> onDeleted()
        }
    }
    val initialNote = stringResource(R.string.envelope_initial_note)
    EnvelopeEditorScreen(
        state = state,
        onDraftChange = viewModel::onDraftChange,
        onSave = { viewModel.save(initialNote) },
        onDelete = viewModel::delete,
        onClose = onClose,
    )
}

@Composable
fun EnvelopeEditorScreen(
    state: EnvelopeEditorUiState,
    onDraftChange: (EnvelopeDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.envelope_editor_new else R.string.envelope_editor_edit))
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
                EnvelopeForm(
                    draft = draft,
                    isNew = state.isNew,
                    errors = state.errors,
                    onDraftChange = onDraftChange,
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
            text = stringResource(R.string.envelope_delete_text),
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
private fun EnvelopeForm(
    draft: EnvelopeDraft,
    isNew: Boolean,
    errors: Map<EnvelopeField, FieldError>,
    onDraftChange: (EnvelopeDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = stringResource(R.string.field_name),
            error = errors[EnvelopeField.NAME],
            supportingText = stringResource(R.string.envelope_field_name_hint),
        )
        FormLabel(stringResource(R.string.envelope_field_role))
        ChoiceChips(
            options = EnvelopeRole.entries,
            selected = draft.role,
            onSelect = { onDraftChange(draft.copy(role = it)) },
            label = { stringResource(it.labelRes()) },
        )
        HintText(stringResource(draft.role.hintRes()))
        FormLabel(stringResource(R.string.envelope_field_policy))
        ChoiceChips(
            options = EnvelopePolicyType.entries,
            selected = draft.policyType,
            onSelect = { onDraftChange(draft.copy(policyType = it)) },
            label = { stringResource(it.labelRes()) },
        )
        HintText(stringResource(draft.policyType.hintRes()))
        when (draft.policyType) {
            EnvelopePolicyType.FLEXIBLE, EnvelopePolicyType.UNTIL_TARGET -> Unit
            EnvelopePolicyType.PERIOD_LIMIT -> MoneyField(
                value = draft.limit,
                onValueChange = { onDraftChange(draft.copy(limit = it)) },
                label = stringResource(R.string.envelope_field_limit),
                error = errors[EnvelopeField.LIMIT],
            )
            EnvelopePolicyType.LOCKED_UNTIL -> DateField(
                date = draft.lockedUntil,
                onDateChange = { date: LocalDate -> onDraftChange(draft.copy(lockedUntil = date)) },
                label = stringResource(R.string.envelope_field_locked_until),
            )
        }
        MoneyField(
            value = draft.target,
            onValueChange = { onDraftChange(draft.copy(target = it)) },
            label = stringResource(
                if (draft.policyType == EnvelopePolicyType.UNTIL_TARGET) {
                    R.string.envelope_field_target
                } else {
                    R.string.envelope_field_target_optional
                },
            ),
            error = errors[EnvelopeField.TARGET],
            imeAction = if (isNew) ImeAction.Next else ImeAction.Done,
        )
        if (isNew) {
            MoneyField(
                value = draft.initialBalance,
                onValueChange = { onDraftChange(draft.copy(initialBalance = it)) },
                label = stringResource(R.string.envelope_field_initial),
                error = errors[EnvelopeField.INITIAL_BALANCE],
                supportingText = stringResource(R.string.envelope_field_initial_hint),
                imeAction = ImeAction.Done,
            )
        }
    }
}
