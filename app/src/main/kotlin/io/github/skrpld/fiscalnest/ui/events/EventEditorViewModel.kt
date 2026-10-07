/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeStatus
import io.github.skrpld.fiscalnest.domain.form.EventDraft
import io.github.skrpld.fiscalnest.domain.form.EventField
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.deleteEvent
import io.github.skrpld.fiscalnest.domain.model.upsertEvent
import io.github.skrpld.fiscalnest.ui.envelopes.defaultEnvelopeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * State of the event editor.
 *
 * @property draft the form, `null` while an existing event loads
 * @property errors invalid fields; shown after the first save attempt
 * @property isDone the event was saved or deleted, or does not exist: the editor should close
 * @property envelopes envelopes the money can arrive in or be paid from
 */
data class EventEditorUiState(
    val isNew: Boolean,
    val draft: EventDraft?,
    val errors: Map<EventField, FieldError> = emptyMap(),
    val isDone: Boolean = false,
    val envelopes: List<EnvelopeStatus> = emptyList(),
)

class EventEditorViewModel(
    private val repository: BudgetRepository,
    private val dateProvider: DateProvider,
    private val idGenerator: IdGenerator,
    private val eventId: String?,
    initialKind: EventKind?,
) : ViewModel() {
    /**
     * Held in Compose state rather than a flow, so text fields update synchronously while typing.
     */
    var uiState by mutableStateOf(
        EventEditorUiState(
            isNew = eventId == null,
            draft = if (eventId == null) {
                EventDraft.new(dateProvider.today(), initialKind ?: EventKind.MANDATORY_EXPENSE)
            } else {
                null
            },
        ),
    )
        private set

    private var validateOnChange = false

    init {
        if (eventId != null) {
            viewModelScope.launch {
                val event = repository.data.first().events.firstOrNull { it.id == eventId }
                uiState = if (event == null) uiState.copy(isDone = true) else uiState.copy(draft = EventDraft.from(event))
            }
        }
        // Follows the envelopes, so one created from the editor shows up and gets preselected.
        viewModelScope.launch {
            repository.data.collect { data ->
                val envelopes = EnvelopeCalculator.summary(data, dateProvider.today()).statuses
                val draft = uiState.draft
                val keepsEnvelope = draft?.envelopeId != null && envelopes.any { it.envelope.id == draft.envelopeId }
                uiState = uiState.copy(
                    envelopes = envelopes,
                    draft = if (draft != null && !keepsEnvelope && eventId == null) {
                        draft.copy(envelopeId = envelopes.defaultEnvelopeId())
                    } else {
                        draft
                    },
                )
            }
        }
    }

    fun onDraftChange(draft: EventDraft) {
        uiState = uiState.copy(draft = draft, errors = if (validateOnChange) errorsOf(draft) else emptyMap())
    }

    fun save() {
        val draft = uiState.draft ?: return
        when (val result = draft.validate(eventId ?: idGenerator.newId())) {
            is Validation.Valid -> viewModelScope.launch {
                repository.update { it.upsertEvent(result.value) }
                uiState = uiState.copy(isDone = true)
            }
            is Validation.Invalid -> {
                validateOnChange = true
                uiState = uiState.copy(errors = result.errors)
            }
        }
    }

    fun delete() {
        val id = eventId ?: return
        viewModelScope.launch {
            repository.update { it.deleteEvent(id) }
            uiState = uiState.copy(isDone = true)
        }
    }

    private fun errorsOf(draft: EventDraft): Map<EventField, FieldError> =
        (draft.validate(eventId ?: "draft") as? Validation.Invalid)?.errors.orEmpty()
}
