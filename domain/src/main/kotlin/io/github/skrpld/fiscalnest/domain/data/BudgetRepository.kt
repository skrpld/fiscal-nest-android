/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.data

import io.github.skrpld.fiscalnest.domain.model.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate

/**
 * Single source of truth for everything the app stores.
 */
interface BudgetRepository {
    /** Current data, re-emitted after every change. */
    val data: Flow<AppData>

    /** Atomically replaces the data with the result of [transform]. */
    suspend fun update(transform: (AppData) -> AppData)
}

/**
 * Keeps the data in memory. Used by tests and previews.
 */
class InMemoryBudgetRepository(initial: AppData = AppData()) : BudgetRepository {
    private val state = MutableStateFlow(initial)

    override val data: StateFlow<AppData> = state.asStateFlow()

    /** The data as of now. */
    val current: AppData get() = state.value

    override suspend fun update(transform: (AppData) -> AppData) {
        state.update(transform)
    }
}

/**
 * Supplies the current date, so time-dependent logic can be tested.
 */
fun interface DateProvider {
    fun today(): LocalDate

    companion object {
        /** Today in the device's current time zone. */
        val System: DateProvider = DateProvider { LocalDate.now() }

        /** Today according to [clock]. */
        fun of(clock: Clock): DateProvider = DateProvider { LocalDate.now(clock) }

        /** Always [date]. */
        fun fixed(date: LocalDate): DateProvider = DateProvider { date }
    }
}

/**
 * Generates identifiers for new events and spending.
 */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random: IdGenerator = IdGenerator { java.util.UUID.randomUUID().toString() }
    }
}
