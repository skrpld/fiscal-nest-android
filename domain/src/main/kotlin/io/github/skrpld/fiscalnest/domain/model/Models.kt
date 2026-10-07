/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

@file:UseSerializers(BigDecimalSerializer::class, LocalDateSerializer::class)

package io.github.skrpld.fiscalnest.domain.model

import fiscalnest.core.CashReserve
import fiscalnest.core.PiggyBankMode
import fiscalnest.core.TopupMode
import io.github.skrpld.fiscalnest.domain.serialization.BigDecimalSerializer
import io.github.skrpld.fiscalnest.domain.serialization.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal
import java.time.LocalDate

/**
 * What a scheduled event does to the budget.
 */
@Serializable
enum class EventKind {
    INCOME,
    MANDATORY_EXPENSE,
    OPTIONAL_EXPENSE,
}

/**
 * When a scheduled event repeats. Mirrors `fiscalnest.core.EventRecurrence`.
 */
@Serializable
sealed interface Recurrence {
    /** A single occurrence on the start date. */
    @Serializable
    @SerialName("once")
    data object Once : Recurrence

    /** The start date, then every [days] days. */
    @Serializable
    @SerialName("every_n_days")
    data class EveryNDays(val days: Int) : Recurrence

    /** On [dayOfMonth] every [months] months; `31` means the last day of the month. */
    @Serializable
    @SerialName("every_n_months")
    data class EveryNMonths(val months: Int, val dayOfMonth: Int) : Recurrence
}

/**
 * A scheduled income or expense with the display data the engine does not know about.
 *
 * @property id stable identifier, also passed to the engine
 * @property name label shown to the user
 * @property amount non-negative amount of each occurrence
 * @property startDate first possible occurrence, anchors the recurrence
 * @property endDate last possible occurrence, inclusive; `null` means open-ended
 */
@Serializable
data class BudgetEvent(
    val id: String,
    val name: String,
    val kind: EventKind,
    val amount: BigDecimal,
    val recurrence: Recurrence,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
) {
    /** `true` when no occurrence can happen on or after [date]. */
    fun hasEndedBefore(date: LocalDate): Boolean =
        (endDate != null && endDate < date) || (recurrence == Recurrence.Once && startDate < date)
}

/**
 * Unscheduled spending logged by the user. Spending dated inside the current period, up to today,
 * is passed to the engine as `alreadySpent`.
 */
@Serializable
data class Spending(
    val id: String,
    val amount: BigDecimal,
    val date: LocalDate,
    val note: String = "",
)

/**
 * How budget periods are laid out on the calendar.
 */
@Serializable
sealed interface PeriodRule {
    /** Calendar-month periods starting on [startDay] (`1..28`), e.g. the 5th to the 4th. */
    @Serializable
    @SerialName("monthly")
    data class Monthly(val startDay: Int = 1) : PeriodRule

    /** Periods of [days] days, aligned so that one of them starts on [anchor]. */
    @Serializable
    @SerialName("fixed_length")
    data class FixedLength(val days: Int, val anchor: LocalDate) : PeriodRule
}

/**
 * A cushion criticality level as the user edits it. Ratios are on the `0..1` scale.
 * Mirrors `fiscalnest.core.CriticalityLevel`.
 */
@Serializable
data class CushionLevel(
    val name: String,
    val maxFillRatio: BigDecimal,
    val topupMode: TopupMode,
    val topupRatio: BigDecimal,
    val admissibilityRatio: BigDecimal,
)

/**
 * Budget rules: everything needed to build the engine configuration and the forecast input.
 *
 * @property forecastPeriods number of periods projected, the current one included
 * @property piggyBankTarget ratio of the post-cushion remainder or a fixed amount, see [piggyBankMode]
 * @property moneyScale decimal places of money, `0` or `2`
 * @property currencyCode ISO 4217 code; `null` uses the currency of the device locale
 */
@Serializable
data class BudgetSettings(
    val period: PeriodRule = PeriodRule.Monthly(startDay = 1),
    val forecastPeriods: Int = DEFAULT_FORECAST_PERIODS,
    val cushionCurrent: BigDecimal = BigDecimal.ZERO,
    val cushionTarget: BigDecimal = BigDecimal.ZERO,
    val piggyBankMode: PiggyBankMode = PiggyBankMode.PERCENT_OF_REMAINDER,
    val piggyBankTarget: BigDecimal = BigDecimal("0.10"),
    val piggyBankAdmissibility: BigDecimal = BigDecimal.ONE,
    val cashReserves: Set<CashReserve> = setOf(CashReserve.CUSHION_TOPUP),
    val cushionLevels: List<CushionLevel> = defaultCushionLevels(),
    val moneyScale: Int = 2,
    val currencyCode: String? = null,
) {
    companion object {
        const val DEFAULT_FORECAST_PERIODS: Int = 6

        /**
         * The two levels from the engine documentation: below 30% take 20% of the target (at most
         * 80% of the remainder), below 70% take 10% of the remainder (at most 50% of it).
         */
        fun defaultCushionLevels(
            criticalName: String = "Critical",
            warningName: String = "Warning",
        ): List<CushionLevel> = listOf(
            CushionLevel(
                name = criticalName,
                maxFillRatio = BigDecimal("0.30"),
                topupMode = TopupMode.PERCENT_OF_TARGET,
                topupRatio = BigDecimal("0.20"),
                admissibilityRatio = BigDecimal("0.80"),
            ),
            CushionLevel(
                name = warningName,
                maxFillRatio = BigDecimal("0.70"),
                topupMode = TopupMode.PERCENT_OF_REMAINDER,
                topupRatio = BigDecimal("0.10"),
                admissibilityRatio = BigDecimal("0.50"),
            ),
        )
    }
}

/**
 * Light or dark appearance choice.
 */
@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Appearance preferences.
 *
 * @property dynamicColor use the wallpaper-based system palette where the device supports it
 */
@Serializable
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

/**
 * When money may be taken out of an [Envelope].
 */
@Serializable
sealed interface EnvelopePolicy {
    /** Withdrawals at any time, up to the balance. */
    @Serializable
    @SerialName("flexible")
    data object Flexible : EnvelopePolicy

    /** At most [limit] withdrawn in each budget period. */
    @Serializable
    @SerialName("period_limit")
    data class PeriodLimit(val limit: BigDecimal) : EnvelopePolicy

    /** No withdrawals before [date], like a term deposit. */
    @Serializable
    @SerialName("locked_until")
    data class LockedUntil(val date: LocalDate) : EnvelopePolicy

    /** No withdrawals until the balance reaches the envelope's [Envelope.target]. */
    @Serializable
    @SerialName("until_target")
    data object UntilTarget : EnvelopePolicy
}

/**
 * A share of the user's savings set aside for one purpose. Envelopes are kept apart from the
 * period plan: the engine never sees them, and their balance comes from [EnvelopeOperation]s.
 *
 * @property target amount the user wants to collect; required by [EnvelopePolicy.UntilTarget]
 */
@Serializable
data class Envelope(
    val id: String,
    val name: String,
    val policy: EnvelopePolicy = EnvelopePolicy.Flexible,
    val target: BigDecimal? = null,
)

/**
 * Direction of an [EnvelopeOperation].
 */
@Serializable
enum class EnvelopeOperationType {
    DEPOSIT,
    WITHDRAWAL,
}

/**
 * Money put into or taken out of an envelope.
 *
 * @property amount positive amount of the operation
 */
@Serializable
data class EnvelopeOperation(
    val id: String,
    val envelopeId: String,
    val type: EnvelopeOperationType,
    val amount: BigDecimal,
    val date: LocalDate,
    val note: String = "",
)

/**
 * Everything the app stores. Persisted as one JSON document and used as the backup format.
 *
 * @property onboardingCompleted `false` until the getting started guide has been finished or
 * skipped. Defaults to `true` so that data saved before the guide existed does not show it; a
 * fresh installation starts with `false`.
 */
@Serializable
data class AppData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val events: List<BudgetEvent> = emptyList(),
    val spendings: List<Spending> = emptyList(),
    val settings: BudgetSettings = BudgetSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val envelopes: List<Envelope> = emptyList(),
    val envelopeOperations: List<EnvelopeOperation> = emptyList(),
    val onboardingCompleted: Boolean = true,
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}
