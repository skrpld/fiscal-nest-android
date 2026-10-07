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
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeCalculator
import io.github.skrpld.fiscalnest.domain.form.QuickSetupDraft
import io.github.skrpld.fiscalnest.domain.form.Validation
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopePolicy
import io.github.skrpld.fiscalnest.ui.envelopes.EnvelopesScreen
import io.github.skrpld.fiscalnest.ui.envelopes.EnvelopesUiState
import io.github.skrpld.fiscalnest.ui.onboarding.OnboardingScreen
import io.github.skrpld.fiscalnest.ui.theme.FiscalNestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * Renders the envelopes tab and the getting started guide on the JVM with Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class NewScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun showsEnvelopes() {
        val data = AppData(
            envelopes = listOf(Envelope("vacation", "Vacation", EnvelopePolicy.LockedUntil(Today.plusMonths(2)))),
            envelopeOperations = listOf(
                EnvelopeOperation("op", "vacation", EnvelopeOperationType.DEPOSIT, BigDecimal("1000"), Today),
            ),
        )
        var opened: String? = null
        composeRule.setContent {
            FiscalNestTheme(darkTheme = false, dynamicColor = false) {
                EnvelopesScreen(
                    state = EnvelopesUiState.Ready(EnvelopeCalculator.summary(data, Today)),
                    onOpenEnvelope = { opened = it },
                    onAddEnvelope = {},
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.envelopes_total)).assertIsDisplayed()
        composeRule.onNodeWithText("Vacation").performClick()
        assertEquals("vacation", opened)
    }

    @Test
    fun invitesToAddEnvelopesWhenEmpty() {
        var added = 0
        composeRule.setContent {
            FiscalNestTheme(darkTheme = false, dynamicColor = false) {
                EnvelopesScreen(
                    state = EnvelopesUiState.Ready(EnvelopeCalculator.summary(AppData(), Today)),
                    onOpenEnvelope = {},
                    onAddEnvelope = { added++ },
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.envelopes_empty_title)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.action_add_envelope)).onFirst().performClick()
        assertEquals(1, added)
    }

    @Test
    fun walksThroughTheGuideToTheQuickSetup() {
        var submitted: QuickSetupDraft? = null
        composeRule.setContent {
            FiscalNestTheme(darkTheme = false, dynamicColor = false) {
                OnboardingScreen(
                    showSetup = true,
                    onSubmitSetup = { draft ->
                        submitted = draft
                        draft.validate()
                    },
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.onboarding_welcome_title)).assertIsDisplayed()
        repeat(4) {
            composeRule.onNodeWithText(text(R.string.onboarding_next)).performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText(text(R.string.onboarding_setup_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.onboarding_start)).performClick()
        assertEquals(QuickSetupDraft(), submitted)
    }

    @Test
    fun skipsTheGuide() {
        var skipped = 0
        composeRule.setContent {
            FiscalNestTheme(darkTheme = false, dynamicColor = false) {
                OnboardingScreen(
                    showSetup = true,
                    onSubmitSetup = { Validation.Invalid(emptyMap()) },
                    onSkip = { skipped++ },
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.onboarding_skip)).performClick()
        assertEquals(1, skipped)
    }
}
