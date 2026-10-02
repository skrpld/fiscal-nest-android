/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.robolectric

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.Today
import io.github.skrpld.fiscalnest.domain.engine.BudgetEngine
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.sampleData
import io.github.skrpld.fiscalnest.ui.overview.OverviewScreen
import io.github.skrpld.fiscalnest.ui.overview.OverviewUiState
import io.github.skrpld.fiscalnest.ui.theme.FiscalNestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Renders the overview on the JVM with Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class OverviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun show(data: AppData, onAddEvent: () -> Unit = {}) {
        val state = OverviewUiState.Ready(
            today = Today,
            hasEvents = data.events.isNotEmpty(),
            outcome = BudgetEngine.forecast(data, Today),
            recentSpendings = emptyList(),
        )
        composeRule.setContent {
            FiscalNestTheme(darkTheme = false, dynamicColor = false) {
                OverviewScreen(
                    state = state,
                    onAddSpending = { Validation.Invalid(emptyMap()) },
                    onAddEvent = onAddEvent,
                    onOpenPeriod = {},
                    onOpenSpendings = {},
                )
            }
        }
    }

    @Test
    fun showsTheDailyBudget() {
        show(sampleData())

        composeRule.onAllNodesWithText(text(R.string.overview_title)).onFirst().assertExists()
        composeRule.onNodeWithText(text(R.string.overview_safe_today)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.action_add_spending)).onFirst().assertExists()
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
                    onAddEvent = {},
                    onOpenPeriod = {},
                    onOpenSpendings = {},
                )
            }
        }
        composeRule.onAllNodesWithText(text(R.string.overview_title)).onFirst().assertExists()
    }
}
