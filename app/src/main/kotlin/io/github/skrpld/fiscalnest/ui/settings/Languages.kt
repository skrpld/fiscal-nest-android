/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skrpld.fiscalnest.R
import java.util.Locale

/**
 * Language tags of the bundled translations, in the order they are offered. Must match
 * `res/xml/locales_config.xml`.
 */
val AppLanguageTags: List<String> = listOf("en", "ru", "zh-Hans", "es", "pt-BR", "fr", "de", "hi", "ar", "ja")

/** `true` when the system lets the app choose its own language (Android 13+). */
val isAppLanguageSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * The bundled language chosen for the app, or `null` when it follows the system.
 */
fun selectedLanguageTag(context: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
    if (locales.isEmpty) return null
    val locale = locales[0]
    return AppLanguageTags.firstOrNull { it.equals(locale.toLanguageTag(), ignoreCase = true) }
        ?: AppLanguageTags.firstOrNull { Locale.forLanguageTag(it).language == locale.language }
}

/**
 * Switches the app to the language [tag], or back to the system language when it is `null`. The
 * system recreates the activity with the new language. Does nothing before Android 13.
 */
fun setAppLanguage(context: Context, tag: String?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    context.getSystemService(LocaleManager::class.java)?.applicationLocales =
        if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

/** The name of the language [tag] in that language: `Español`, `中文（简体）`. */
fun languageDisplayName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/** The name of the app language, or "System default". */
@Composable
fun currentLanguageName(): String {
    val tag = selectedLanguageTag(LocalContext.current)
    return if (tag == null) stringResource(R.string.settings_language_system) else languageDisplayName(tag)
}

/**
 * Lets the user pick the app language among the bundled translations. Only shown on Android 13+.
 */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected = selectedLanguageTag(context)
    val select: (String?) -> Unit = { tag ->
        onDismiss()
        if (tag != selected) setAppLanguage(context, tag)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ChoiceRow(
                    selected = selected == null,
                    text = stringResource(R.string.settings_language_system),
                    onClick = { select(null) },
                )
                AppLanguageTags.forEach { tag ->
                    ChoiceRow(
                        selected = selected == tag,
                        text = languageDisplayName(tag),
                        onClick = { select(tag) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
