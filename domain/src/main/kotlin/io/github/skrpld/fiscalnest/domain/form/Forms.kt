/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.form

import fiscalnest.core.PiggyBankMode
import fiscalnest.core.TopupMode
import io.github.skrpld.fiscalnest.domain.engine.WhatIfValues
import io.github.skrpld.fiscalnest.domain.input.DecimalInput
import io.github.skrpld.fiscalnest.domain.model.BudgetEvent
import io.github.skrpld.fiscalnest.domain.model.CushionLevel
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopePolicy
import io.github.skrpld.fiscalnest.domain.model.EnvelopeRole
import io.github.skrpld.fiscalnest.domain.model.EventKind
import io.github.skrpld.fiscalnest.domain.model.Limits
import io.github.skrpld.fiscalnest.domain.model.PeriodRule
import io.github.skrpld.fiscalnest.domain.model.Recurrence
import io.github.skrpld.fiscalnest.domain.model.Spending
import java.math.BigDecimal
import java.time.LocalDate

/** Repetition choices of the event editor. */
enum class RecurrenceType { ONCE, DAYS, MONTHS }

/** Fields of the event editor. */
enum class EventField { NAME, AMOUNT, ENVELOPE, INTERVAL, DAY_OF_MONTH, END_DATE }

/**
 * Text state of the event editor.
 *
 * @property interval step in days or months, depending on [recurrenceType]
 * @property endDate last possible occurrence; ignored for one-time events
 * @property envelopeId envelope the money arrives in or is paid from; required
 */
data class EventDraft(
    val name: String,
    val kind: EventKind,
    val amount: String,
    val recurrenceType: RecurrenceType,
    val interval: String,
    val dayOfMonth: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val envelopeId: String? = null,
) {
    /** Builds the event with [id], or reports every invalid field. */
    fun validate(id: String): Validation<BudgetEvent, EventField> {
        val errors = ErrorCollector<EventField>()
        val trimmedName = name.trim()
        when {
            trimmedName.isEmpty() -> errors.add(EventField.NAME, FieldError.REQUIRED)
            trimmedName.length > Limits.MAX_NAME_LENGTH -> errors.add(EventField.NAME, FieldError.TOO_LONG)
        }
        val parsedAmount = positiveAmount(amount, EventField.AMOUNT, errors)
        if (envelopeId == null) errors.add(EventField.ENVELOPE, FieldError.REQUIRED)
        val step = when (recurrenceType) {
            RecurrenceType.ONCE -> null
            RecurrenceType.DAYS -> intInRange(interval, 1..Limits.MAX_RECURRENCE_DAYS, EventField.INTERVAL, errors)
            RecurrenceType.MONTHS -> intInRange(interval, 1..Limits.MAX_RECURRENCE_MONTHS, EventField.INTERVAL, errors)
        }
        val day = if (recurrenceType == RecurrenceType.MONTHS) {
            intInRange(dayOfMonth, 1..31, EventField.DAY_OF_MONTH, errors)
        } else {
            null
        }
        val effectiveEnd = if (recurrenceType == RecurrenceType.ONCE) null else endDate
        if (effectiveEnd != null && effectiveEnd < startDate) errors.add(EventField.END_DATE, FieldError.END_BEFORE_START)
        return errors.result {
            BudgetEvent(
                id = id,
                name = trimmedName,
                kind = kind,
                amount = parsedAmount!!,
                recurrence = when (recurrenceType) {
                    RecurrenceType.ONCE -> Recurrence.Once
                    RecurrenceType.DAYS -> Recurrence.EveryNDays(step!!)
                    RecurrenceType.MONTHS -> Recurrence.EveryNMonths(step!!, day!!)
                },
                startDate = startDate,
                endDate = effectiveEnd,
                envelopeId = envelopeId,
            )
        }
    }

    companion object {
        /** An empty monthly event starting [today], paid through the envelope with [envelopeId]. */
        fun new(
            today: LocalDate,
            kind: EventKind = EventKind.MANDATORY_EXPENSE,
            envelopeId: String? = null,
        ): EventDraft = EventDraft(
            name = "",
            kind = kind,
            amount = "",
            recurrenceType = RecurrenceType.MONTHS,
            interval = "1",
            dayOfMonth = today.dayOfMonth.toString(),
            startDate = today,
            endDate = null,
            envelopeId = envelopeId,
        )

        /** The editor state of an existing [event]. */
        fun from(event: BudgetEvent): EventDraft {
            val recurrence = event.recurrence
            return EventDraft(
                name = event.name,
                kind = event.kind,
                amount = DecimalInput.formatAmount(event.amount),
                recurrenceType = when (recurrence) {
                    Recurrence.Once -> RecurrenceType.ONCE
                    is Recurrence.EveryNDays -> RecurrenceType.DAYS
                    is Recurrence.EveryNMonths -> RecurrenceType.MONTHS
                },
                interval = when (recurrence) {
                    Recurrence.Once -> "1"
                    is Recurrence.EveryNDays -> recurrence.days.toString()
                    is Recurrence.EveryNMonths -> recurrence.months.toString()
                },
                dayOfMonth = when (recurrence) {
                    is Recurrence.EveryNMonths -> recurrence.dayOfMonth.toString()
                    else -> event.startDate.dayOfMonth.toString()
                },
                startDate = event.startDate,
                endDate = event.endDate,
                envelopeId = event.envelopeId,
            )
        }
    }
}

/** Fields of the spending form. */
enum class SpendingField { AMOUNT, ENVELOPE, NOTE, DATE }

/**
 * Text state of the spending form.
 */
data class SpendingDraft(
    val amount: String = "",
    val note: String = "",
    val date: LocalDate,
    val envelopeId: String? = null,
) {
    /**
     * Builds the spending with [id]. Spending is always taken from an envelope, cannot exceed
     * [available] and cannot be dated after [today].
     *
     * @param available what the chosen envelope allows to withdraw on [date]
     */
    fun validate(
        id: String,
        today: LocalDate,
        available: BigDecimal = BigDecimal.ZERO,
    ): Validation<Spending, SpendingField> {
        val errors = ErrorCollector<SpendingField>()
        val parsedAmount = positiveAmount(amount, SpendingField.AMOUNT, errors)
        if (envelopeId == null) {
            errors.add(SpendingField.ENVELOPE, FieldError.REQUIRED)
        } else if (parsedAmount != null && parsedAmount > available) {
            errors.add(SpendingField.AMOUNT, FieldError.EXCEEDS_AVAILABLE)
        }
        val trimmedNote = note.trim()
        if (trimmedNote.length > Limits.MAX_NOTE_LENGTH) errors.add(SpendingField.NOTE, FieldError.TOO_LONG)
        if (date > today) errors.add(SpendingField.DATE, FieldError.IN_THE_FUTURE)
        return errors.result { Spending(id = id, amount = parsedAmount!!, date = date, note = trimmedNote, envelopeId = envelopeId) }
    }

    companion object {
        /** The form state of an existing [spending]. */
        fun from(spending: Spending): SpendingDraft = SpendingDraft(
            amount = DecimalInput.formatAmount(spending.amount),
            note = spending.note,
            date = spending.date,
            envelopeId = spending.envelopeId,
        )
    }
}

/** Fields of the cushion level form. */
enum class CushionLevelField { NAME, MAX_FILL, TOPUP, ADMISSIBILITY }

/**
 * Text state of the cushion level form; percentages are on the `0..100` scale.
 */
data class CushionLevelDraft(
    val name: String = "",
    val maxFillPercent: String = "",
    val topupMode: TopupMode = TopupMode.PERCENT_OF_TARGET,
    val topupPercent: String = "",
    val admissibilityPercent: String = "100",
) {
    /**
     * Builds the level. Its threshold must differ from those of [otherLevels], as the engine
     * requires distinct thresholds.
     */
    fun validate(otherLevels: List<CushionLevel>): Validation<CushionLevel, CushionLevelField> {
        val errors = ErrorCollector<CushionLevelField>()
        val trimmedName = name.trim()
        when {
            trimmedName.isEmpty() -> errors.add(CushionLevelField.NAME, FieldError.REQUIRED)
            trimmedName.length > Limits.MAX_NAME_LENGTH -> errors.add(CushionLevelField.NAME, FieldError.TOO_LONG)
        }
        val maxFill = percent(maxFillPercent, CushionLevelField.MAX_FILL, errors)
        if (maxFill != null && maxFill.signum() == 0) errors.add(CushionLevelField.MAX_FILL, FieldError.MUST_BE_POSITIVE)
        if (maxFill != null && otherLevels.any { it.maxFillRatio.compareTo(maxFill) == 0 }) {
            errors.add(CushionLevelField.MAX_FILL, FieldError.DUPLICATE)
        }
        val topup = percent(topupPercent, CushionLevelField.TOPUP, errors)
        val admissibility = percent(admissibilityPercent, CushionLevelField.ADMISSIBILITY, errors)
        return errors.result {
            CushionLevel(
                name = trimmedName,
                maxFillRatio = maxFill!!,
                topupMode = topupMode,
                topupRatio = topup!!,
                admissibilityRatio = admissibility!!,
            )
        }
    }

    companion object {
        fun from(level: CushionLevel): CushionLevelDraft = CushionLevelDraft(
            name = level.name,
            maxFillPercent = DecimalInput.formatPercent(level.maxFillRatio),
            topupMode = level.topupMode,
            topupPercent = DecimalInput.formatPercent(level.topupRatio),
            admissibilityPercent = DecimalInput.formatPercent(level.admissibilityRatio),
        )
    }
}

/** Fields of the what-if form. */
enum class WhatIfField { INCOME, MANDATORY, OPTIONAL, CUSHION_CURRENT, CUSHION_TARGET }

/**
 * Text state of the what-if form. Blank fields count as zero.
 */
data class WhatIfDraft(
    val income: String = "",
    val mandatory: String = "",
    val optional: String = "",
    val cushionCurrent: String = "",
    val cushionTarget: String = "",
) {
    fun validate(): Validation<WhatIfValues, WhatIfField> {
        val errors = ErrorCollector<WhatIfField>()
        val values = listOf(
            WhatIfField.INCOME to income,
            WhatIfField.MANDATORY to mandatory,
            WhatIfField.OPTIONAL to optional,
            WhatIfField.CUSHION_CURRENT to cushionCurrent,
            WhatIfField.CUSHION_TARGET to cushionTarget,
        ).associate { (field, text) -> field to optionalAmount(text, field, errors) }
        return errors.result {
            WhatIfValues(
                income = values.getValue(WhatIfField.INCOME)!!,
                mandatory = values.getValue(WhatIfField.MANDATORY)!!,
                optional = values.getValue(WhatIfField.OPTIONAL)!!,
                cushionCurrent = values.getValue(WhatIfField.CUSHION_CURRENT)!!,
                cushionTarget = values.getValue(WhatIfField.CUSHION_TARGET)!!,
            )
        }
    }

    /** `true` when no field has been filled in yet. */
    val isEmpty: Boolean
        get() = listOf(income, mandatory, optional, cushionCurrent, cushionTarget).all { it.isBlank() }

    /** `true` once an income or expense amount has been typed; the cushion alone is not enough. */
    val hasAmounts: Boolean
        get() = listOf(income, mandatory, optional).any { it.isNotBlank() }
}

/** Fields of the cushion balance form. */
enum class CushionField { CURRENT, TARGET }

/** Validated cushion balances. */
data class CushionValues(val current: BigDecimal, val target: BigDecimal)

/**
 * Text state of the cushion balance form. Blank fields count as zero.
 */
data class CushionDraft(val current: String, val target: String) {
    fun validate(): Validation<CushionValues, CushionField> {
        val errors = ErrorCollector<CushionField>()
        val parsedCurrent = optionalAmount(current, CushionField.CURRENT, errors)
        val parsedTarget = optionalAmount(target, CushionField.TARGET, errors)
        return errors.result { CushionValues(parsedCurrent!!, parsedTarget!!) }
    }

    companion object {
        fun from(current: BigDecimal, target: BigDecimal): CushionDraft =
            CushionDraft(DecimalInput.formatAmount(current), DecimalInput.formatAmount(target))
    }
}

/** Fields of the piggy bank form. */
enum class PiggyBankField { TARGET, ADMISSIBILITY }

/** Validated piggy bank rules; [target] is a ratio or an amount depending on [mode]. */
data class PiggyBankValues(val mode: PiggyBankMode, val target: BigDecimal, val admissibility: BigDecimal)

/**
 * Text state of the piggy bank form. [target] is a percentage of the post-cushion remainder in
 * [PiggyBankMode.PERCENT_OF_REMAINDER] mode and an amount in [PiggyBankMode.FIXED_AMOUNT] mode.
 */
data class PiggyBankDraft(
    val mode: PiggyBankMode,
    val target: String,
    val admissibilityPercent: String,
) {
    fun validate(): Validation<PiggyBankValues, PiggyBankField> {
        val errors = ErrorCollector<PiggyBankField>()
        val parsedTarget = when (mode) {
            PiggyBankMode.PERCENT_OF_REMAINDER -> percent(target, PiggyBankField.TARGET, errors)
            PiggyBankMode.FIXED_AMOUNT -> optionalAmount(target, PiggyBankField.TARGET, errors)
        }
        val admissibility = percent(admissibilityPercent, PiggyBankField.ADMISSIBILITY, errors)
        return errors.result { PiggyBankValues(mode, parsedTarget!!, admissibility!!) }
    }

    companion object {
        fun from(mode: PiggyBankMode, target: BigDecimal, admissibility: BigDecimal): PiggyBankDraft = PiggyBankDraft(
            mode = mode,
            target = when (mode) {
                PiggyBankMode.PERCENT_OF_REMAINDER -> DecimalInput.formatPercent(target)
                PiggyBankMode.FIXED_AMOUNT -> DecimalInput.formatAmount(target)
            },
            admissibilityPercent = DecimalInput.formatPercent(admissibility),
        )
    }
}

/** Period layouts offered in the settings. */
enum class PeriodType { MONTHLY, FIXED_LENGTH }

/** Fields of the period form. */
enum class PeriodField { START_DAY, LENGTH }

/**
 * Text state of the period form.
 */
data class PeriodDraft(
    val type: PeriodType,
    val startDay: String,
    val lengthDays: String,
    val anchor: LocalDate,
) {
    fun validate(): Validation<PeriodRule, PeriodField> {
        val errors = ErrorCollector<PeriodField>()
        return when (type) {
            PeriodType.MONTHLY -> {
                val day = intInRange(startDay, 1..Limits.MAX_MONTHLY_START_DAY, PeriodField.START_DAY, errors)
                errors.result { PeriodRule.Monthly(day!!) }
            }
            PeriodType.FIXED_LENGTH -> {
                val days = intInRange(lengthDays, 1..Limits.MAX_PERIOD_DAYS, PeriodField.LENGTH, errors)
                errors.result { PeriodRule.FixedLength(days!!, anchor) }
            }
        }
    }

    companion object {
        fun from(rule: PeriodRule, today: LocalDate): PeriodDraft = when (rule) {
            is PeriodRule.Monthly -> PeriodDraft(PeriodType.MONTHLY, rule.startDay.toString(), "14", today)
            is PeriodRule.FixedLength -> PeriodDraft(PeriodType.FIXED_LENGTH, "1", rule.days.toString(), rule.anchor)
        }
    }
}

/** Spending rules offered in the envelope editor. */
enum class EnvelopePolicyType { FLEXIBLE, PERIOD_LIMIT, LOCKED_UNTIL, UNTIL_TARGET }

/** Fields of the envelope editor. */
enum class EnvelopeField { NAME, LIMIT, TARGET, INITIAL_BALANCE }

/**
 * A validated envelope.
 *
 * @property initialBalance amount to deposit when the envelope is created; `0` for none
 */
data class EnvelopeValues(val envelope: Envelope, val initialBalance: BigDecimal)

/**
 * Text state of the envelope editor.
 *
 * @property limit withdrawal limit per period, used by [EnvelopePolicyType.PERIOD_LIMIT]
 * @property lockedUntil first day withdrawals are allowed, used by [EnvelopePolicyType.LOCKED_UNTIL]
 * @property target optional goal; required by [EnvelopePolicyType.UNTIL_TARGET]
 * @property initialBalance optional first deposit of a new envelope
 */
data class EnvelopeDraft(
    val name: String,
    val role: EnvelopeRole,
    val policyType: EnvelopePolicyType,
    val limit: String,
    val lockedUntil: LocalDate,
    val target: String,
    val initialBalance: String,
) {
    /** Builds the envelope with [id], or reports every invalid field. */
    fun validate(id: String): Validation<EnvelopeValues, EnvelopeField> {
        val errors = ErrorCollector<EnvelopeField>()
        val trimmedName = name.trim()
        when {
            trimmedName.isEmpty() -> errors.add(EnvelopeField.NAME, FieldError.REQUIRED)
            trimmedName.length > Limits.MAX_NAME_LENGTH -> errors.add(EnvelopeField.NAME, FieldError.TOO_LONG)
        }
        val parsedLimit = if (policyType == EnvelopePolicyType.PERIOD_LIMIT) {
            positiveAmount(limit, EnvelopeField.LIMIT, errors)
        } else {
            null
        }
        val parsedTarget = when {
            policyType == EnvelopePolicyType.UNTIL_TARGET -> positiveAmount(target, EnvelopeField.TARGET, errors)
            target.isBlank() -> null
            else -> positiveAmount(target, EnvelopeField.TARGET, errors)
        }
        val parsedInitial = optionalAmount(initialBalance, EnvelopeField.INITIAL_BALANCE, errors)
        return errors.result {
            EnvelopeValues(
                envelope = Envelope(
                    id = id,
                    name = trimmedName,
                    policy = when (policyType) {
                        EnvelopePolicyType.FLEXIBLE -> EnvelopePolicy.Flexible
                        EnvelopePolicyType.PERIOD_LIMIT -> EnvelopePolicy.PeriodLimit(parsedLimit!!)
                        EnvelopePolicyType.LOCKED_UNTIL -> EnvelopePolicy.LockedUntil(lockedUntil)
                        EnvelopePolicyType.UNTIL_TARGET -> EnvelopePolicy.UntilTarget
                    },
                    target = parsedTarget,
                    role = role,
                ),
                initialBalance = parsedInitial!!,
            )
        }
    }

    companion object {
        /** Default lock of a new envelope: half a year ahead. */
        private const val DEFAULT_LOCK_MONTHS = 6L

        /** An empty flexible envelope without a role. */
        fun new(today: LocalDate): EnvelopeDraft = EnvelopeDraft(
            name = "",
            role = EnvelopeRole.GENERAL,
            policyType = EnvelopePolicyType.FLEXIBLE,
            limit = "",
            lockedUntil = today.plusMonths(DEFAULT_LOCK_MONTHS),
            target = "",
            initialBalance = "",
        )

        /** The editor state of an existing [envelope]. */
        fun from(envelope: Envelope, today: LocalDate): EnvelopeDraft {
            val policy = envelope.policy
            return EnvelopeDraft(
                name = envelope.name,
                role = envelope.role,
                policyType = when (policy) {
                    EnvelopePolicy.Flexible -> EnvelopePolicyType.FLEXIBLE
                    is EnvelopePolicy.PeriodLimit -> EnvelopePolicyType.PERIOD_LIMIT
                    is EnvelopePolicy.LockedUntil -> EnvelopePolicyType.LOCKED_UNTIL
                    EnvelopePolicy.UntilTarget -> EnvelopePolicyType.UNTIL_TARGET
                },
                limit = (policy as? EnvelopePolicy.PeriodLimit)?.let { DecimalInput.formatAmount(it.limit) }.orEmpty(),
                lockedUntil = (policy as? EnvelopePolicy.LockedUntil)?.date ?: today.plusMonths(DEFAULT_LOCK_MONTHS),
                target = envelope.target?.let { DecimalInput.formatAmount(it) }.orEmpty(),
                initialBalance = "",
            )
        }
    }
}

/** Fields of the envelope deposit and withdrawal form. */
enum class EnvelopeOperationField { AMOUNT, NOTE, DATE }

/**
 * Text state of the envelope deposit and withdrawal form.
 */
data class EnvelopeOperationDraft(
    val amount: String = "",
    val note: String = "",
    val date: LocalDate,
) {
    /**
     * Builds the operation with [id]. It cannot be dated after [today], and a withdrawal cannot
     * exceed [available].
     *
     * @param available what the envelope policy allows to withdraw on [date]; ignored for deposits
     */
    fun validate(
        id: String,
        envelopeId: String,
        type: EnvelopeOperationType,
        today: LocalDate,
        available: BigDecimal,
    ): Validation<EnvelopeOperation, EnvelopeOperationField> {
        val errors = ErrorCollector<EnvelopeOperationField>()
        val parsedAmount = positiveAmount(amount, EnvelopeOperationField.AMOUNT, errors)
        if (parsedAmount != null && type == EnvelopeOperationType.WITHDRAWAL && parsedAmount > available) {
            errors.add(EnvelopeOperationField.AMOUNT, FieldError.EXCEEDS_AVAILABLE)
        }
        val trimmedNote = note.trim()
        if (trimmedNote.length > Limits.MAX_NOTE_LENGTH) errors.add(EnvelopeOperationField.NOTE, FieldError.TOO_LONG)
        if (date > today) errors.add(EnvelopeOperationField.DATE, FieldError.IN_THE_FUTURE)
        return errors.result {
            EnvelopeOperation(
                id = id,
                envelopeId = envelopeId,
                type = type,
                amount = parsedAmount!!,
                date = date,
                note = trimmedNote,
            )
        }
    }
}

internal fun <F> positiveAmount(text: String, field: F, errors: ErrorCollector<F>): BigDecimal? {
    if (text.isBlank()) {
        errors.add(field, FieldError.REQUIRED)
        return null
    }
    val value = DecimalInput.parse(text)
    return when {
        value == null -> {
            errors.add(field, FieldError.INVALID_NUMBER)
            null
        }
        value.signum() == 0 -> {
            errors.add(field, FieldError.MUST_BE_POSITIVE)
            null
        }
        else -> value
    }
}

internal fun <F> optionalAmount(text: String, field: F, errors: ErrorCollector<F>): BigDecimal? {
    if (text.isBlank()) return BigDecimal.ZERO
    return DecimalInput.parse(text) ?: run {
        errors.add(field, FieldError.INVALID_NUMBER)
        null
    }
}

private fun <F> percent(text: String, field: F, errors: ErrorCollector<F>): BigDecimal? {
    if (text.isBlank()) {
        errors.add(field, FieldError.REQUIRED)
        return null
    }
    if (DecimalInput.parse(text) == null) {
        errors.add(field, FieldError.INVALID_NUMBER)
        return null
    }
    return DecimalInput.parsePercent(text) ?: run {
        errors.add(field, FieldError.OUT_OF_RANGE)
        null
    }
}

internal fun <F> intInRange(text: String, range: IntRange, field: F, errors: ErrorCollector<F>): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        errors.add(field, FieldError.REQUIRED)
        return null
    }
    val value = trimmed.toIntOrNull() ?: run {
        errors.add(field, FieldError.INVALID_NUMBER)
        return null
    }
    if (value !in range) {
        errors.add(field, FieldError.OUT_OF_RANGE)
        return null
    }
    return value
}

/** Fields of the form that confirms an event occurrence. */
enum class OccurrenceField { AMOUNT, ENVELOPE }

/**
 * A confirmed occurrence: how much actually moved, and through which envelope.
 */
data class OccurrenceValues(val envelopeId: String, val amount: BigDecimal)

/**
 * Text state of the form that confirms an event occurrence. The amount may differ from the
 * planned one, for example when a bill came out higher.
 */
data class OccurrenceDraft(
    val amount: String,
    val envelopeId: String?,
) {
    /**
     * @param isIncome an income adds money; an expense cannot exceed [available]
     * @param available what the chosen envelope allows to withdraw on the occurrence date
     */
    fun validate(isIncome: Boolean, available: BigDecimal): Validation<OccurrenceValues, OccurrenceField> {
        val errors = ErrorCollector<OccurrenceField>()
        val parsedAmount = positiveAmount(amount, OccurrenceField.AMOUNT, errors)
        if (envelopeId == null) {
            errors.add(OccurrenceField.ENVELOPE, FieldError.REQUIRED)
        } else if (!isIncome && parsedAmount != null && parsedAmount > available) {
            errors.add(OccurrenceField.AMOUNT, FieldError.EXCEEDS_AVAILABLE)
        }
        return errors.result { OccurrenceValues(envelopeId!!, parsedAmount!!) }
    }

    companion object {
        /** The planned amount of [event], through its envelope. */
        fun from(event: BudgetEvent): OccurrenceDraft =
            OccurrenceDraft(amount = DecimalInput.formatAmount(event.amount), envelopeId = event.envelopeId)
    }
}
