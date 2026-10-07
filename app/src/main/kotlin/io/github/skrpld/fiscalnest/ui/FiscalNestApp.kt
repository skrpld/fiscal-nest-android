/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.ui.envelopes.EnvelopeDetailRoute
import io.github.skrpld.fiscalnest.ui.envelopes.EnvelopeEditorRoute
import io.github.skrpld.fiscalnest.ui.events.EventEditorRoute
import io.github.skrpld.fiscalnest.ui.navigation.CushionLevelsDestination
import io.github.skrpld.fiscalnest.ui.navigation.EnvelopeDetailDestination
import io.github.skrpld.fiscalnest.ui.navigation.EnvelopeEditorDestination
import io.github.skrpld.fiscalnest.ui.navigation.EventEditorDestination
import io.github.skrpld.fiscalnest.ui.navigation.MainDestination
import io.github.skrpld.fiscalnest.ui.navigation.OnboardingDestination
import io.github.skrpld.fiscalnest.ui.navigation.PeriodDetailDestination
import io.github.skrpld.fiscalnest.ui.navigation.SpendingsDestination
import io.github.skrpld.fiscalnest.ui.onboarding.OnboardingRoute
import io.github.skrpld.fiscalnest.ui.period.PeriodDetailRoute
import io.github.skrpld.fiscalnest.ui.settings.CushionLevelsRoute
import io.github.skrpld.fiscalnest.ui.spending.SpendingsRoute

/** Slide offset of the Material shared-axis transition: a quarter of the width. */
private fun slideOffset(fullWidth: Int): Int = fullWidth / 4

/**
 * Root of the UI: the tabbed home plus the detail screens on top of it.
 */
@Composable
fun FiscalNestApp(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = MainDestination,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, initialOffset = ::slideOffset) + fadeIn()
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, targetOffset = ::slideOffset) + fadeOut()
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, initialOffset = ::slideOffset) + fadeIn()
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, targetOffset = ::slideOffset) + fadeOut()
        },
    ) {
        composable<MainDestination> {
            MainScreen(
                onAddEvent = { kind -> navController.navigate(EventEditorDestination(kind = kind?.name)) },
                onOpenEvent = { id -> navController.navigate(EventEditorDestination(eventId = id)) },
                onOpenPeriod = { index -> navController.navigate(PeriodDetailDestination(index)) },
                onOpenSpendings = { navController.navigate(SpendingsDestination) },
                onOpenCushionLevels = { navController.navigate(CushionLevelsDestination) },
                onOpenEnvelope = { id -> navController.navigate(EnvelopeDetailDestination(id)) },
                onAddEnvelope = { navController.navigate(EnvelopeEditorDestination()) },
                onOpenGuide = { navController.navigate(OnboardingDestination) },
            )
        }
        composable<EventEditorDestination> { entry ->
            val route = entry.toRoute<EventEditorDestination>()
            EventEditorRoute(
                eventId = route.eventId,
                initialKind = route.kind?.let { name -> EventKind.entries.firstOrNull { it.name == name } },
                onClose = dropUnlessResumed { navController.popBackStack() },
                onCreateEnvelope = { navController.navigate(EnvelopeEditorDestination()) },
            )
        }
        composable<PeriodDetailDestination> { entry ->
            PeriodDetailRoute(
                index = entry.toRoute<PeriodDetailDestination>().index,
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<SpendingsDestination> {
            SpendingsRoute(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onCreateEnvelope = { navController.navigate(EnvelopeEditorDestination()) },
            )
        }
        composable<CushionLevelsDestination> {
            CushionLevelsRoute(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<EnvelopeDetailDestination> { entry ->
            val envelopeId = entry.toRoute<EnvelopeDetailDestination>().envelopeId
            EnvelopeDetailRoute(
                envelopeId = envelopeId,
                onBack = dropUnlessResumed { navController.popBackStack() },
                onEdit = { navController.navigate(EnvelopeEditorDestination(envelopeId)) },
            )
        }
        composable<EnvelopeEditorDestination> { entry ->
            val envelopeId = entry.toRoute<EnvelopeEditorDestination>().envelopeId
            EnvelopeEditorRoute(
                envelopeId = envelopeId,
                onClose = dropUnlessResumed { navController.popBackStack() },
                onDeleted = dropUnlessResumed {
                    // The detail screen of a deleted envelope has nothing left to show.
                    navController.popBackStack<MainDestination>(inclusive = false)
                },
            )
        }
        composable<OnboardingDestination> {
            OnboardingRoute(isFirstLaunch = false, onFinish = dropUnlessResumed { navController.popBackStack() })
        }
    }
}
