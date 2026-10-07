/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.robolectric

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.Today
import io.github.skrpld.fiscalnest.domain.engine.BudgetEngine
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.startQueue
import io.github.skrpld.fiscalnest.domain.queue.ConfirmationQueue
import io.github.skrpld.fiscalnest.domain.queue.PendingOccurrence
import io.github.skrpld.fiscalnest.sampleData
import io.github.skrpld.fiscalnest.ui.overview.OverviewScreen
import io.github.skrpld.fiscalnest.ui.overview.OverviewUiState
import io.github.skrpld.fiscalnest.ui.theme.FiscalNestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Renders the overview on the JVM with Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class OverviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun show(data: AppData, onAddEvent: () -> Unit = {}, onSkip: (PendingOccurrence) -> Unit = {}) {
        val state = OverviewUiState.Ready(
            today = Today,
            hasEvents = data.events.isNotEmpty(),
            outcome = BudgetEngine.forecast(data, Today),
            recentSpendings = emptyList(),
            envelopes = EnvelopeCalculator.summary(data, Today).statuses,
            pending = ConfirmationQueue.pending(data.startQueue(LocalDate.of(2026, 8, 1)), Today),
        )
        composeRule.setContent {
            FiscalNestTheme(darkTheme = false, dynamicColor = false) {
                OverviewScreen(
                    state = state,
                    onAddSpending = { Validation.Invalid(emptyMap()) },
                    onConfirmOccurrence = { _, _ -> Validation.Invalid(emptyMap()) },
                    onSkipOccurrence = onSkip,
                    onReopenOccurrence = {},
                    onAddEvent = onAddEvent,
                    onOpenPeriod = {},
                    onOpenSpendings = {},
                    onCreateEnvelope = {},
                )
            }
        }
    }

    @Test
    fun showsTheDailyBudget() {
        show(sampleData())

        composeRule.onAllNodesWithText(text(R.string.overview_title)).onFirst().assertExists()
        composeRule.onNodeWithText(text(R.string.overview_safe_today)).assertIsDisplayed()
        val addSpending = text(R.string.action_add_spending)
        val fab = composeRule.onAllNodes(hasText(addSpending) or hasContentDescription(addSpending), useUnmergedTree = true)
        assertTrue(
            "No '$addSpending' node in:\n" + composeRule.onRoot(useUnmergedTree = true).printToString(),
            fab.fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun listsDueOperationsForConfirmation() {
        val skipped = mutableListOf<String>()
        show(sampleData(), onSkip = { skipped += it.event.id })

        composeRule.onNodeWithText(text(R.string.queue_title)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Salary").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Rent").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.queue_skip)).onFirst().performClick()
        assertEquals(listOf("salary"), skipped)
    }

    @Test
    fun invitesToAddEventsWhenEmpty() {
        var addEventClicks = 0
        show(AppData(), onAddEvent = { addEventClicks++ })

        composeRule.onNodeWithText(text(R.string.overview_no_events_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_add_event)).performClick()
        assertEquals(1, addEventClicks)
    }

    @Test
    fun rendersInDarkTheme() {
        composeRule.setContent {
            FiscalNestTheme(darkTheme = true, dynamicColor = true) {
                OverviewScreen(
                    state = OverviewUiState.Loading,
                    onAddSpending = { Validation.Invalid(emptyMap()) },
                    onConfirmOccurrence = { _, _ -> Validation.Invalid(emptyMap()) },
                    onSkipOccurrence = {},
                    onReopenOccurrence = {},
                    onAddEvent = {},
                    onOpenPeriod = {},
                    onOpenSpendings = {},
                    onCreateEnvelope = {},
                )
            }
        }
        composeRule.onAllNodesWithText(text(R.string.overview_title)).onFirst().assertExists()
    }
}
