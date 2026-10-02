/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest

import android.app.Application
import android.content.Context
import io.github.skrpld.fiscalnest.data.DataStoreBudgetRepository
import io.github.skrpld.fiscalnest.data.createAppDataStore
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.BudgetSettings

class FiscalNestApplication : Application() {
    /** App-wide dependencies, created on first use. */
    val container: AppContainer by lazy { DefaultAppContainer(this) }
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val repository: BudgetRepository by lazy {
        DataStoreBudgetRepository(createAppDataStore(appContext, initialData()))
    }

    override val dateProvider: DateProvider = DateProvider.System

    override val idGenerator: IdGenerator = IdGenerator.Random

    override fun initialData(): AppData = AppData(
        settings = BudgetSettings(
            cushionLevels = BudgetSettings.defaultCushionLevels(
                criticalName = appContext.getString(R.string.cushion_level_default_critical),
                warningName = appContext.getString(R.string.cushion_level_default_warning),
            ),
        ),
    )
}
