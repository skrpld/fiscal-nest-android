/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.model

/**
 * Inserts [event], or replaces the event with the same id in place.
 */
fun AppData.upsertEvent(event: BudgetEvent): AppData = copy(events = events.upsert(event) { it.id })

/**
 * Removes the event with [id], if any.
 */
fun AppData.deleteEvent(id: String): AppData = copy(events = events.filterNot { it.id == id })

/**
 * Inserts [spending], or replaces the spending with the same id in place.
 */
fun AppData.upsertSpending(spending: Spending): AppData = copy(spendings = spendings.upsert(spending) { it.id })

/**
 * Removes the spending with [id], if any.
 */
fun AppData.deleteSpending(id: String): AppData = copy(spendings = spendings.filterNot { it.id == id })

/**
 * Applies [transform] to the budget settings.
 */
fun AppData.updateSettings(transform: (BudgetSettings) -> BudgetSettings): AppData =
    copy(settings = transform(settings))

/**
 * Applies [transform] to the appearance settings.
 */
fun AppData.updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings): AppData =
    copy(appearance = transform(appearance))

/**
 * Replaces the cushion levels, sorted by fill threshold as the engine requires.
 */
fun BudgetSettings.withCushionLevels(levels: List<CushionLevel>): BudgetSettings =
    copy(cushionLevels = levels.sortedBy { it.maxFillRatio })

private inline fun <T> List<T>.upsert(item: T, key: (T) -> String): List<T> {
    val id = key(item)
    return if (any { key(it) == id }) map { if (key(it) == id) item else it } else this + item
}
