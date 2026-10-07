/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.form.FieldError
import io.github.skrpld.fiscalnest.domain.form.QuickSetupDraft
import io.github.skrpld.fiscalnest.domain.form.QuickSetupField
import io.github.skrpld.fiscalnest.domain.form.QuickSetupValues
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.form.applyQuickSetup
import io.github.skrpld.fiscalnest.domain.model.completeOnboarding
import io.github.skrpld.fiscalnest.ui.common.IntegerField
import io.github.skrpld.fiscalnest.ui.common.MoneyField
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.settings.LanguageDialog
import io.github.skrpld.fiscalnest.ui.settings.currentLanguageName
import io.github.skrpld.fiscalnest.ui.settings.isAppLanguageSupported
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val repository: BudgetRepository,
    private val dateProvider: DateProvider,
    private val idGenerator: IdGenerator,
) : ViewModel() {
    /**
     * Applies the quick setup and finishes the guide when the setup is valid.
     *
     * @param incomeName label of the income event, in the user's language
     * @return the validation result, so the form can show field errors
     */
    fun finishWithSetup(draft: QuickSetupDraft, incomeName: String): Validation<QuickSetupValues, QuickSetupField> {
        val result = draft.validate()
        if (result is Validation.Valid) {
            val incomeId = idGenerator.newId()
            val today = dateProvider.today()
            viewModelScope.launch { repository.update { it.applyQuickSetup(result.value, incomeId, incomeName, today) } }
        }
        return result
    }

    /** Finishes the guide without changing the budget. */
    fun finish() {
        viewModelScope.launch { repository.update { it.completeOnboarding() } }
    }
}

/** A page of the guide that explains a part of the app. */
private data class GuidePage(val icon: ImageVector, val title: Int, val text: Int)

private val GuidePages = listOf(
    GuidePage(Icons.Rounded.WavingHand, R.string.onboarding_welcome_title, R.string.onboarding_welcome_text),
    GuidePage(Icons.Rounded.Event, R.string.onboarding_events_title, R.string.onboarding_events_text),
    GuidePage(Icons.Rounded.Today, R.string.onboarding_daily_title, R.string.onboarding_daily_text),
    GuidePage(Icons.Rounded.Savings, R.string.onboarding_savings_title, R.string.onboarding_savings_text),
)

/**
 * The getting started guide.
 *
 * @param isFirstLaunch ends with the quick setup and marks the guide as finished; when `false`
 * the guide is opened again from the settings and only explains the app
 * @param onFinish called when the guide closes; on the first launch the app replaces it by itself
 */
@Composable
fun OnboardingRoute(
    isFirstLaunch: Boolean,
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = appViewModel { OnboardingViewModel(it.repository, it.dateProvider, it.idGenerator) },
) {
    val incomeName = stringResource(R.string.onboarding_income_name)
    OnboardingScreen(
        showSetup = isFirstLaunch,
        onSubmitSetup = { draft ->
            val result = viewModel.finishWithSetup(draft, incomeName)
            if (result is Validation.Valid) onFinish()
            result
        },
        onSkip = {
            if (isFirstLaunch) viewModel.finish()
            onFinish()
        },
    )
}

/**
 * @param showSetup adds the quick setup as the last page
 * @param onSubmitSetup applies the quick setup and returns its validation result
 * @param onSkip closes the guide without the quick setup
 */
@Composable
fun OnboardingScreen(
    showSetup: Boolean,
    onSubmitSetup: (QuickSetupDraft) -> Validation<QuickSetupValues, QuickSetupField>,
    onSkip: () -> Unit,
) {
    val pageCount = GuidePages.size + if (showSetup) 1 else 0
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    var income by rememberSaveable { mutableStateOf("") }
    var payDay by rememberSaveable { mutableStateOf("") }
    var cushionCurrent by rememberSaveable { mutableStateOf("") }
    var cushionTarget by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<QuickSetupField, FieldError>>(emptyMap()) }
    val page = pagerState.currentPage
    val isLast = page == pageCount - 1

    BackHandler(enabled = page > 0) {
        scope.launch { pagerState.animateScrollToPage(page - 1) }
    }

    val finish: () -> Unit = {
        if (showSetup) {
            val result = onSubmitSetup(QuickSetupDraft(income, payDay, cushionCurrent, cushionTarget))
            if (result is Validation.Invalid) errors = result.errors
        } else {
            onSkip()
        }
    }

    Scaffold(
        bottomBar = {
            OnboardingControls(
                page = page,
                pageCount = pageCount,
                isLast = isLast,
                showSetup = showSetup,
                onBack = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                onNext = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
                onFinish = finish,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!isLast || !showSetup) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(if (showSetup) R.string.onboarding_skip else R.string.action_close))
                    }
                } else {
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip_setup)) }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { index ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    if (index < GuidePages.size) {
                        GuidePageContent(GuidePages[index], showLanguage = index == 0)
                    } else {
                        QuickSetupContent(
                            draft = QuickSetupDraft(income, payDay, cushionCurrent, cushionTarget),
                            errors = errors,
                            onDraftChange = { draft ->
                                income = draft.income
                                payDay = draft.payDay
                                cushionCurrent = draft.cushionCurrent
                                cushionTarget = draft.cushionTarget
                                errors = emptyMap()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidePageContent(page: GuidePage, showLanguage: Boolean) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Text(
            text = stringResource(page.title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(page.text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (showLanguage && isAppLanguageSupported) {
            OutlinedButton(onClick = { showLanguageDialog = true }) {
                Icon(Icons.Rounded.Language, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(currentLanguageName())
            }
        }
    }
    if (showLanguageDialog) {
        LanguageDialog(onDismiss = { showLanguageDialog = false })
    }
}

@Composable
private fun QuickSetupContent(
    draft: QuickSetupDraft,
    errors: Map<QuickSetupField, FieldError>,
    onDraftChange: (QuickSetupDraft) -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(stringResource(R.string.onboarding_setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.onboarding_setup_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyField(
            value = draft.income,
            onValueChange = { onDraftChange(draft.copy(income = it)) },
            label = stringResource(R.string.onboarding_field_income),
            error = errors[QuickSetupField.INCOME],
        )
        IntegerField(
            value = draft.payDay,
            onValueChange = { onDraftChange(draft.copy(payDay = it)) },
            label = stringResource(R.string.onboarding_field_pay_day),
            error = errors[QuickSetupField.PAY_DAY],
            supportingText = stringResource(R.string.onboarding_field_pay_day_hint),
        )
        Text(
            text = stringResource(R.string.onboarding_setup_cushion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyField(
            value = draft.cushionCurrent,
            onValueChange = { onDraftChange(draft.copy(cushionCurrent = it)) },
            label = stringResource(R.string.field_cushion_current),
            error = errors[QuickSetupField.CUSHION_CURRENT],
        )
        MoneyField(
            value = draft.cushionTarget,
            onValueChange = { onDraftChange(draft.copy(cushionTarget = it)) },
            label = stringResource(R.string.field_cushion_target),
            error = errors[QuickSetupField.CUSHION_TARGET],
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun OnboardingControls(
    page: Int,
    pageCount: Int,
    isLast: Boolean,
    showSetup: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val indicatorDescription = stringResource(R.string.onboarding_page, page + 1, pageCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (page > 0) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
        Row(
            modifier = Modifier.semantics { contentDescription = indicatorDescription },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(pageCount) { index ->
                val color by animateColorAsState(
                    targetValue = if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "page-indicator",
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Button(onClick = if (isLast) onFinish else onNext) {
                Text(
                    stringResource(
                        when {
                            !isLast -> R.string.onboarding_next
                            showSetup -> R.string.onboarding_start
                            else -> R.string.action_done
                        },
                    ),
                )
            }
        }
    }
}
