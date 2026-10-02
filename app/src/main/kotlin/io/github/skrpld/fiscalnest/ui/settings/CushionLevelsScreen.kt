/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import fiscalnest.core.TopupMode
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.form.CushionLevelDraft
import io.github.skrpld.fiscalnest.domain.form.CushionLevelField
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.CushionLevel
import io.github.skrpld.fiscalnest.domain.model.Limits
import io.github.skrpld.fiscalnest.domain.model.updateSettings
import io.github.skrpld.fiscalnest.domain.model.withCushionLevels
import io.github.skrpld.fiscalnest.ui.common.ChoiceChips
import io.github.skrpld.fiscalnest.ui.common.FabClearance
import io.github.skrpld.fiscalnest.ui.common.FormTextField
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.LocalPercentFormatter
import io.github.skrpld.fiscalnest.ui.common.PercentField
import io.github.skrpld.fiscalnest.ui.common.StatusBanner
import io.github.skrpld.fiscalnest.ui.common.BannerTone
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.labelRes
import io.github.skrpld.fiscalnest.ui.common.withContentPadding
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of the cushion levels screen; `null` while loading.
 *
 * @property levels sorted by fill threshold, as the engine applies them
 */
data class CushionLevelsUiState(val levels: List<CushionLevel>)

class CushionLevelsViewModel(private val repository: BudgetRepository) : ViewModel() {
    val uiState: StateFlow<CushionLevelsUiState?> = repository.data
        .map { CushionLevelsUiState(it.settings.cushionLevels.sortedBy { level -> level.maxFillRatio }) }
        // Eager, so that save() always validates against the stored levels.
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Replaces the level at [index] of the sorted list, or adds a level when [index] is `null`.
     *
     * @return the validation result, so the form can show field errors
     */
    fun save(index: Int?, draft: CushionLevelDraft): Validation<CushionLevel, CushionLevelField> {
        val levels = uiState.value?.levels.orEmpty()
        val others = if (index == null) levels else levels.filterIndexed { i, _ -> i != index }
        val result = draft.validate(others)
        if (result is Validation.Valid) {
            viewModelScope.launch {
                repository.update { data -> data.updateSettings { it.withCushionLevels(others + result.value) } }
            }
        }
        return result
    }

    /** Removes the level at [index]; the last remaining level cannot be removed. */
    fun delete(index: Int) {
        val levels = uiState.value?.levels.orEmpty()
        if (levels.size <= 1 || index !in levels.indices) return
        viewModelScope.launch {
            repository.update { data ->
                data.updateSettings { it.withCushionLevels(levels.filterIndexed { i, _ -> i != index }) }
            }
        }
    }
}

@Composable
fun CushionLevelsRoute(
    onBack: () -> Unit,
    viewModel: CushionLevelsViewModel = appViewModel { CushionLevelsViewModel(it.repository) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CushionLevelsScreen(state, onBack, onSave = viewModel::save, onDelete = viewModel::delete)
}

@Composable
fun CushionLevelsScreen(
    state: CushionLevelsUiState?,
    onBack: () -> Unit,
    onSave: (Int?, CushionLevelDraft) -> Validation<CushionLevel, CushionLevelField>,
    onDelete: (Int) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editorIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    val canAdd = state != null && state.levels.size < Limits.MAX_CUSHION_LEVELS
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.cushion_levels_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (canAdd) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editorIndex = null
                        editorOpen = true
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.cushion_levels_add)) },
                )
            }
        },
    ) { padding ->
        if (state == null) {
            LoadingContent(Modifier.padding(padding))
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = padding.withContentPadding(extraBottom = FabClearance),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        StatusBanner(
                            tone = BannerTone.INFO,
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.cushion_levels_explanation_title),
                            text = stringResource(R.string.cushion_levels_explanation),
                        )
                    }
                    itemsIndexed(state.levels, key = { _, level -> level.maxFillRatio.toPlainString() }) { index, level ->
                        LevelCard(
                            level = level,
                            onClick = {
                                editorIndex = index
                                editorOpen = true
                            },
                        )
                    }
                }
            }
        }
    }
    if (editorOpen && state != null) {
        val index = editorIndex
        val level = index?.let { state.levels.getOrNull(it) }
        LevelDialog(
            initial = level?.let { CushionLevelDraft.from(it) } ?: CushionLevelDraft(),
            isNew = level == null,
            canDelete = level != null && state.levels.size > 1,
            onSave = { draft -> onSave(if (level == null) null else index, draft) },
            onDelete = {
                if (index != null) onDelete(index)
                editorOpen = false
            },
            onDismiss = { editorOpen = false },
        )
    }
}

@Composable
private fun LevelCard(level: CushionLevel, onClick: () -> Unit) {
    val percent = LocalPercentFormatter.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(level.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.cushion_level_threshold, percent.format(level.maxFillRatio)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(
                    if (level.topupMode == TopupMode.PERCENT_OF_TARGET) {
                        R.string.cushion_level_rule_target
                    } else {
                        R.string.cushion_level_rule_remainder
                    },
                    percent.format(level.topupRatio),
                    percent.format(level.admissibilityRatio),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelDialog(
    initial: CushionLevelDraft,
    isNew: Boolean,
    canDelete: Boolean,
    onSave: (CushionLevelDraft) -> Validation<CushionLevel, CushionLevelField>,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var maxFill by rememberSaveable { mutableStateOf(initial.maxFillPercent) }
    var mode by rememberSaveable { mutableStateOf(initial.topupMode) }
    var topup by rememberSaveable { mutableStateOf(initial.topupPercent) }
    var admissibility by rememberSaveable { mutableStateOf(initial.admissibilityPercent) }
    var errors by remember { mutableStateOf<Map<CushionLevelField, FieldError>>(emptyMap()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.cushion_level_new else R.string.cushion_level_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.field_name),
                    error = errors[CushionLevelField.NAME],
                )
                PercentField(
                    value = maxFill,
                    onValueChange = { maxFill = it },
                    label = stringResource(R.string.cushion_level_max_fill),
                    supportingText = stringResource(R.string.cushion_level_max_fill_hint),
                    error = errors[CushionLevelField.MAX_FILL],
                )
                ChoiceChips(
                    options = TopupMode.entries,
                    selected = mode,
                    onSelect = { mode = it },
                    label = { stringResource(it.labelRes()) },
                )
                PercentField(
                    value = topup,
                    onValueChange = { topup = it },
                    label = stringResource(R.string.cushion_level_topup),
                    error = errors[CushionLevelField.TOPUP],
                )
                PercentField(
                    value = admissibility,
                    onValueChange = { admissibility = it },
                    label = stringResource(R.string.cushion_level_admissibility),
                    supportingText = stringResource(R.string.cushion_level_admissibility_hint),
                    error = errors[CushionLevelField.ADMISSIBILITY],
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val result = onSave(CushionLevelDraft(name, maxFill, mode, topup, admissibility))) {
                        is Validation.Valid -> onDismiss()
                        is Validation.Invalid -> errors = result.errors
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Text(stringResource(R.string.action_delete))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
