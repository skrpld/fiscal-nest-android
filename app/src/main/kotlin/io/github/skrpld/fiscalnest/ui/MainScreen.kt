/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.ui.envelopes.EnvelopesRoute
import io.github.skrpld.fiscalnest.ui.events.EventsRoute
import io.github.skrpld.fiscalnest.ui.overview.OverviewRoute
import io.github.skrpld.fiscalnest.ui.settings.SettingsRoute
import io.github.skrpld.fiscalnest.ui.whatif.WhatIfRoute

/**
 * Top-level destinations, reachable from the navigation bar or rail.
 */
enum class TopLevelTab(
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    OVERVIEW(R.string.tab_overview, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    EVENTS(R.string.tab_events, Icons.Rounded.Event, Icons.Outlined.Event),
    ENVELOPES(R.string.tab_envelopes, Icons.Rounded.Mail, Icons.Outlined.Mail),
    WHAT_IF(R.string.tab_what_if, Icons.Rounded.Calculate, Icons.Outlined.Calculate),
    SETTINGS(R.string.tab_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}

/**
 * The tabbed home of the app. Uses a navigation bar on compact windows and a navigation rail on
 * medium and expanded ones, as the Material adaptive guidelines recommend.
 */
@Composable
fun MainScreen(
    onAddEvent: (EventKind?) -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenPeriod: (Int) -> Unit,
    onOpenSpendings: () -> Unit,
    onOpenCushionLevels: () -> Unit,
    onOpenEnvelope: (String) -> Unit,
    onAddEnvelope: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(TopLevelTab.OVERVIEW) }
    val tabStates = rememberSaveableStateHolder()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useRail = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    BackHandler(enabled = selectedTab != TopLevelTab.OVERVIEW) {
        selectedTab = TopLevelTab.OVERVIEW
    }

    NavigationSuiteScaffoldLayout(
        layoutType = if (useRail) NavigationSuiteType.NavigationRail else NavigationSuiteType.NavigationBar,
        navigationSuite = {
            if (useRail) {
                AppNavigationRail(selectedTab, onSelect = { selectedTab = it })
            } else {
                AppNavigationBar(selectedTab, onSelect = { selectedTab = it })
            }
        },
    ) {
        // The bar or rail already pads for the system bars on its side; the content must not.
        val navigationInsets = if (useRail) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
        } else {
            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
        }
        Box(Modifier.consumeWindowInsets(navigationInsets)) {
            Crossfade(targetState = selectedTab, label = "tabs") { tab ->
                tabStates.SaveableStateProvider(tab.name) {
                    when (tab) {
                        TopLevelTab.OVERVIEW -> OverviewRoute(
                            onAddEvent = { onAddEvent(null) },
                            onOpenPeriod = onOpenPeriod,
                            onOpenSpendings = onOpenSpendings,
                            onCreateEnvelope = onAddEnvelope,
                        )
                        TopLevelTab.EVENTS -> EventsRoute(onAddEvent = onAddEvent, onOpenEvent = onOpenEvent)
                        TopLevelTab.ENVELOPES -> EnvelopesRoute(onOpenEnvelope = onOpenEnvelope, onAddEnvelope = onAddEnvelope)
                        TopLevelTab.WHAT_IF -> WhatIfRoute()
                        TopLevelTab.SETTINGS -> SettingsRoute(
                            onOpenCushionLevels = onOpenCushionLevels,
                            onOpenGuide = onOpenGuide,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavigationBar(selected: TopLevelTab, onSelect: (TopLevelTab) -> Unit) {
    NavigationBar {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (tab == selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(selected: TopLevelTab, onSelect: (TopLevelTab) -> Unit) {
    NavigationRail {
        Spacer(Modifier.weight(1f))
        TopLevelTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (tab == selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
