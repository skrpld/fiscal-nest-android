/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.format.MoneyFormatter
import io.github.skrpld.fiscalnest.domain.format.PercentFormatter
import io.github.skrpld.fiscalnest.domain.model.AppearanceSettings
import io.github.skrpld.fiscalnest.domain.model.ThemeMode
import io.github.skrpld.fiscalnest.ui.FiscalNestApp
import io.github.skrpld.fiscalnest.ui.common.DateTexts
import io.github.skrpld.fiscalnest.ui.common.LocalAppContainer
import io.github.skrpld.fiscalnest.ui.common.LocalDateTexts
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.LocalPercentFormatter
import io.github.skrpld.fiscalnest.ui.theme.FiscalNestTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * App-wide display preferences, needed before the first frame.
 */
sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(
        val appearance: AppearanceSettings,
        val currencyCode: String?,
        val moneyScale: Int,
    ) : MainUiState
}

class MainViewModel(repository: BudgetRepository) : ViewModel() {
    val uiState: StateFlow<MainUiState> = repository.data
        .map { MainUiState.Ready(it.appearance, it.settings.currencyCode, it.settings.moneyScale) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as FiscalNestApplication).container

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(container.repository) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the splash screen until the stored theme is known, so the UI never flashes.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            when (val ready = state) {
                MainUiState.Loading -> Unit
                is MainUiState.Ready -> AppRoot(ready)
            }
        }
    }

    @Composable
    private fun AppRoot(state: MainUiState.Ready) {
        val darkTheme = when (state.appearance.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        // The theme may differ from the system one, so the system bar icons follow the app theme.
        DisposableEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
            )
            onDispose {}
        }
        val locale = LocalConfiguration.current.locales[0]
        val moneyFormatter = remember(locale, state.currencyCode, state.moneyScale) {
            MoneyFormatter(locale, state.currencyCode, state.moneyScale)
        }
        val percentFormatter = remember(locale) { PercentFormatter(locale) }
        val dateTexts = remember(locale) { DateTexts(locale) }
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalMoneyFormatter provides moneyFormatter,
            LocalPercentFormatter provides percentFormatter,
            LocalDateTexts provides dateTexts,
        ) {
            FiscalNestTheme(darkTheme = darkTheme, dynamicColor = state.appearance.dynamicColor) {
                FiscalNestApp()
            }
        }
    }

    private companion object {
        // The default scrims of androidx.activity for three-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
