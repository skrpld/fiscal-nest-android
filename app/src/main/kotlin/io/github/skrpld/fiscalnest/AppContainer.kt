/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest

import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.data.DateProvider
import io.github.skrpld.fiscalnest.domain.data.IdGenerator
import io.github.skrpld.fiscalnest.domain.model.AppData

/**
 * Manual dependency container shared by all screens.
 */
interface AppContainer {
    val repository: BudgetRepository
    val dateProvider: DateProvider
    val idGenerator: IdGenerator

    /** Data of a fresh installation, with default names in the current language. */
    fun initialData(): AppData
}
