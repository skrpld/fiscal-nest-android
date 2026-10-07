/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fiscalnest.core.PiggyBankMode
import io.github.skrpld.fiscalnest.BuildConfig
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.ui.common.ConfirmDialog
import io.github.skrpld.fiscalnest.ui.common.LoadingContent
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter
import io.github.skrpld.fiscalnest.ui.common.LocalPercentFormatter
import io.github.skrpld.fiscalnest.ui.common.appViewModel
import io.github.skrpld.fiscalnest.ui.common.labelRes
import io.github.skrpld.fiscalnest.ui.common.periodRuleText
import io.github.skrpld.fiscalnest.ui.common.withContentPadding
import io.github.skrpld.fiscalnest.ui.theme.isDynamicColorSupported
import java.time.LocalDate

private enum class SettingsDialogType { PERIOD, HORIZON, CURRENCY, CUSHION, PIGGY_BANK, RESERVES, THEME, LANGUAGE, IMPORT, RESET }

@Composable
fun SettingsRoute(
    onOpenCushionLevels: () -> Unit,
    onOpenGuide: () -> Unit,
    viewModel: SettingsViewModel = appViewModel { container ->
        SettingsViewModel(container.repository, container.dateProvider, container::initialData)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val messageTexts = mapOf(
        SettingsMessage.EXPORTED to stringResource(R.string.settings_export_done),
        SettingsMessage.EXPORT_FAILED to stringResource(R.string.settings_export_failed),
        SettingsMessage.IMPORTED to stringResource(R.string.settings_import_done),
        SettingsMessage.IMPORT_FAILED to stringResource(R.string.settings_import_failed),
        SettingsMessage.RESET to stringResource(R.string.settings_reset_done),
    )
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(messageTexts.getValue(message))
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportBackup { context.contentResolver.openOutputStream(uri, "wt") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBackup { context.contentResolver.openInputStream(uri) }
    }

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = SettingsActions(
            viewModel = viewModel,
            onOpenCushionLevels = onOpenCushionLevels,
            onOpenGuide = onOpenGuide,
            onExport = { today -> exportLauncher.launch("fiscal-nest-$today.json") },
            onImport = { importLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "text/plain", "application/octet-stream")) },
        ),
    )
}

private const val BACKUP_MIME_TYPE = "application/json"

/**
 * Callbacks of the settings screen.
 */
class SettingsActions(
    val viewModel: SettingsViewModel,
    val onOpenCushionLevels: () -> Unit,
    val onOpenGuide: () -> Unit,
    val onExport: (LocalDate) -> Unit,
    val onImport: () -> Unit,
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    actions: SettingsActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialogType?>(null) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            SettingsUiState.Loading -> LoadingContent(Modifier.padding(padding))
            is SettingsUiState.Ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                SettingsList(
                    state = state,
                    modifier = Modifier.widthIn(max = 720.dp),
                    padding = padding,
                    onOpenDialog = { dialog = it },
                    actions = actions,
                )
            }
        }
    }
    if (state is SettingsUiState.Ready) {
        SettingsDialogs(dialog, state, actions) { dialog = null }
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState.Ready,
    modifier: Modifier,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onOpenDialog: (SettingsDialogType) -> Unit,
    actions: SettingsActions,
) {
    val settings = state.settings
    val money = LocalMoneyFormatter.current
    val percent = LocalPercentFormatter.current
    val locale = LocalConfiguration.current.locales[0]
    val viewModel = actions.viewModel
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = padding.withContentPadding(horizontal = 0.dp)) {
        item { SectionHeader(stringResource(R.string.settings_section_budget)) }
        item {
            SettingsItem(
                icon = Icons.Rounded.DateRange,
                title = stringResource(R.string.settings_period),
                summary = periodRuleText(settings.period),
                onClick = { onOpenDialog(SettingsDialogType.PERIOD) },
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.Timeline,
                title = stringResource(R.string.settings_horizon),
                summary = pluralStringResource(R.plurals.forecast_periods, settings.forecastPeriods, settings.forecastPeriods),
                onClick = { onOpenDialog(SettingsDialogType.HORIZON) },
            )
        }
        item {
            val currency = money.currency
            SettingsItem(
                icon = Icons.Rounded.AttachMoney,
                title = stringResource(R.string.settings_currency),
                summary = if (settings.currencyCode == null) {
                    stringResource(R.string.settings_currency_device, currency.currencyCode)
                } else {
                    "${currency.currencyCode} · ${currency.getDisplayName(locale)}"
                },
                onClick = { onOpenDialog(SettingsDialogType.CURRENCY) },
            )
        }
        item {
            SwitchItem(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.settings_cents),
                summary = stringResource(R.string.settings_cents_summary),
                checked = settings.moneyScale > 0,
                onCheckedChange = viewModel::setShowCents,
            )
        }

        item { SectionHeader(stringResource(R.string.settings_section_savings)) }
        item {
            SettingsItem(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.settings_cushion),
                summary = stringResource(
                    R.string.settings_cushion_summary,
                    money.format(settings.cushionCurrent),
                    money.format(settings.cushionTarget),
                ),
                onClick = { onOpenDialog(SettingsDialogType.CUSHION) },
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.Layers,
                title = stringResource(R.string.settings_cushion_levels),
                summary = pluralStringResource(R.plurals.cushion_levels_count, settings.cushionLevels.size, settings.cushionLevels.size),
                onClick = actions.onOpenCushionLevels,
                showChevron = true,
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.Savings,
                title = stringResource(R.string.settings_piggy_bank),
                summary = when (settings.piggyBankMode) {
                    PiggyBankMode.PERCENT_OF_REMAINDER -> stringResource(
                        R.string.settings_piggy_percent_summary,
                        percent.format(settings.piggyBankTarget),
                        percent.format(settings.piggyBankAdmissibility),
                    )
                    PiggyBankMode.FIXED_AMOUNT -> stringResource(
                        R.string.settings_piggy_fixed_summary,
                        money.format(settings.piggyBankTarget),
                        percent.format(settings.piggyBankAdmissibility),
                    )
                },
                onClick = { onOpenDialog(SettingsDialogType.PIGGY_BANK) },
            )
        }
        item {
            val reserveNames = settings.cashReserves.sortedBy { it.ordinal }.map { stringResource(it.labelRes()) }
            SettingsItem(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.settings_reserves),
                summary = if (reserveNames.isEmpty()) {
                    stringResource(R.string.settings_reserves_none)
                } else {
                    stringResource(R.string.settings_reserves_summary, reserveNames.joinToString(", "))
                },
                onClick = { onOpenDialog(SettingsDialogType.RESERVES) },
            )
        }

        item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
        item {
            SettingsItem(
                icon = Icons.Rounded.Brightness6,
                title = stringResource(R.string.settings_theme),
                summary = stringResource(state.appearance.themeMode.labelRes()),
                onClick = { onOpenDialog(SettingsDialogType.THEME) },
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.settings_language),
                summary = if (isAppLanguageSupported) {
                    currentLanguageName()
                } else {
                    stringResource(R.string.settings_language_unsupported)
                },
                onClick = if (isAppLanguageSupported) {
                    { onOpenDialog(SettingsDialogType.LANGUAGE) }
                } else {
                    null
                },
            )
        }
        item {
            SwitchItem(
                icon = Icons.Rounded.Wallpaper,
                title = stringResource(R.string.settings_dynamic_color),
                summary = stringResource(
                    if (isDynamicColorSupported) R.string.settings_dynamic_color_summary else R.string.settings_dynamic_color_unsupported,
                ),
                checked = state.appearance.dynamicColor && isDynamicColorSupported,
                enabled = isDynamicColorSupported,
                onCheckedChange = viewModel::setDynamicColor,
            )
        }

        item { SectionHeader(stringResource(R.string.settings_section_data)) }
        item {
            SettingsItem(
                icon = Icons.Rounded.Backup,
                title = stringResource(R.string.settings_export),
                summary = stringResource(R.string.settings_export_summary),
                onClick = { actions.onExport(state.today) },
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.settings_import),
                summary = stringResource(R.string.settings_import_summary),
                onClick = { onOpenDialog(SettingsDialogType.IMPORT) },
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.DeleteForever,
                title = stringResource(R.string.settings_reset),
                summary = stringResource(R.string.settings_reset_summary),
                onClick = { onOpenDialog(SettingsDialogType.RESET) },
            )
        }

        item { SectionHeader(stringResource(R.string.settings_section_about)) }
        item {
            SettingsItem(
                icon = Icons.Rounded.School,
                title = stringResource(R.string.settings_guide),
                summary = stringResource(R.string.settings_guide_summary),
                onClick = actions.onOpenGuide,
                showChevron = true,
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.app_name),
                summary = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.Gavel,
                title = stringResource(R.string.settings_engine),
                summary = stringResource(R.string.settings_engine_summary),
            )
        }
    }
}

@Composable
private fun SettingsDialogs(
    dialog: SettingsDialogType?,
    state: SettingsUiState.Ready,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    val settings = state.settings
    val viewModel = actions.viewModel
    when (dialog) {
        null -> Unit
        SettingsDialogType.PERIOD -> PeriodDialog(
            rule = settings.period,
            today = state.today,
            onConfirm = {
                viewModel.setPeriod(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialogType.HORIZON -> HorizonDialog(
            current = settings.forecastPeriods,
            onConfirm = {
                viewModel.setForecastPeriods(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialogType.CURRENCY -> CurrencyDialog(
            selectedCode = settings.currencyCode,
            locale = LocalConfiguration.current.locales[0],
            onSelect = {
                viewModel.setCurrency(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialogType.CUSHION -> CushionDialog(
            current = settings.cushionCurrent,
            target = settings.cushionTarget,
            onConfirm = {
                viewModel.setCushion(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialogType.PIGGY_BANK -> PiggyBankDialog(
            mode = settings.piggyBankMode,
            target = settings.piggyBankTarget,
            admissibility = settings.piggyBankAdmissibility,
            onConfirm = {
                viewModel.setPiggyBank(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialogType.RESERVES -> ReservesDialog(
            selected = settings.cashReserves,
            onToggle = viewModel::setCashReserve,
            onDismiss = onDismiss,
        )
        SettingsDialogType.THEME -> ThemeDialog(
            selected = state.appearance.themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialogType.LANGUAGE -> LanguageDialog(onDismiss = onDismiss)
        SettingsDialogType.IMPORT -> ConfirmDialog(
            title = stringResource(R.string.settings_import_confirm_title),
            text = stringResource(R.string.settings_import_confirm_text),
            confirmLabel = stringResource(R.string.settings_import_confirm_action),
            icon = Icons.Rounded.Restore,
            onConfirm = {
                onDismiss()
                actions.onImport()
            },
            onDismiss = onDismiss,
        )
        SettingsDialogType.RESET -> ConfirmDialog(
            title = stringResource(R.string.settings_reset_confirm_title),
            text = stringResource(R.string.settings_reset_confirm_text),
            confirmLabel = stringResource(R.string.settings_reset_confirm_action),
            icon = Icons.Rounded.DeleteForever,
            destructive = true,
            onConfirm = {
                onDismiss()
                viewModel.resetAll()
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (showChevron) {
            { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
        } else {
            null
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}
