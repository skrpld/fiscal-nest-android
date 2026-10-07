/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.envelope

import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperation
import io.github.skrpld.fiscalnest.domain.model.EnvelopeOperationType
import io.github.skrpld.fiscalnest.domain.model.EnvelopePolicy
import io.github.skrpld.fiscalnest.domain.model.EnvelopeRole
import io.github.skrpld.fiscalnest.domain.period.DateRange
import io.github.skrpld.fiscalnest.domain.period.PeriodResolver
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Why an envelope's policy holds money back on a date.
 */
sealed interface EnvelopeLock {
    /** [EnvelopePolicy.LockedUntil] has not reached its date. */
    data class UntilDate(val date: LocalDate) : EnvelopeLock

    /** [EnvelopePolicy.UntilTarget] still needs [missing] to reach the target. */
    data class UntilTarget(val missing: BigDecimal) : EnvelopeLock

    /** [EnvelopePolicy.PeriodLimit] is used up for the current period. */
    data object PeriodLimitReached : EnvelopeLock
}

/**
 * An envelope with its figures on a date.
 *
 * @property balance deposits minus withdrawals, all dates included
 * @property withdrawnInPeriod withdrawals dated inside [period]
 * @property available what the policy allows to withdraw on the date, never more than [balance]
 * @property lock why less than the balance is available; `null` when nothing is held back
 * @property period budget period containing the date
 */
data class EnvelopeStatus(
    val envelope: Envelope,
    val balance: BigDecimal,
    val withdrawnInPeriod: BigDecimal,
    val available: BigDecimal,
    val lock: EnvelopeLock?,
    val period: DateRange,
) {
    /** Share of [Envelope.target] collected, `0..1`; `null` without a target. */
    val targetProgress: Float?
        get() {
            val target = envelope.target ?: return null
            if (target.signum() <= 0) return null
            return (balance.toDouble() / target.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

/**
 * Totals of all envelopes on a date.
 *
 * @property statuses one per envelope, in the order they were added
 */
data class EnvelopesSummary(
    val total: BigDecimal,
    val available: BigDecimal,
    val statuses: List<EnvelopeStatus>,
)

/**
 * Applies the envelope policies. Stateless and safe to call from any thread.
 */
object EnvelopeCalculator {
    /**
     * Figures of [envelope] on [date].
     *
     * @param operations operations of any envelope; only those of [envelope] count
     * @param period budget period containing [date], for [EnvelopePolicy.PeriodLimit]
     */
    fun status(
        envelope: Envelope,
        operations: List<EnvelopeOperation>,
        period: DateRange,
        date: LocalDate,
    ): EnvelopeStatus {
        val own = operations.filter { it.envelopeId == envelope.id }
        val balance = own.fold(BigDecimal.ZERO) { total, operation ->
            when (operation.type) {
                EnvelopeOperationType.DEPOSIT -> total + operation.amount
                EnvelopeOperationType.WITHDRAWAL -> total - operation.amount
            }
        }
        val withdrawnInPeriod = own
            .filter { it.type == EnvelopeOperationType.WITHDRAWAL && it.date in period }
            .fold(BigDecimal.ZERO) { total, operation -> total + operation.amount }
        val spendable = balance.max(BigDecimal.ZERO)
        val (allowed, lock) = when (val policy = envelope.policy) {
            EnvelopePolicy.Flexible -> spendable to null
            is EnvelopePolicy.PeriodLimit -> {
                val left = (policy.limit - withdrawnInPeriod).max(BigDecimal.ZERO)
                spendable.min(left) to if (left.signum() == 0) EnvelopeLock.PeriodLimitReached else null
            }
            is EnvelopePolicy.LockedUntil ->
                if (date < policy.date) BigDecimal.ZERO to EnvelopeLock.UntilDate(policy.date) else spendable to null
            EnvelopePolicy.UntilTarget -> {
                val target = envelope.target
                if (target != null && balance < target) {
                    BigDecimal.ZERO to EnvelopeLock.UntilTarget(target - balance)
                } else {
                    spendable to null
                }
            }
        }
        return EnvelopeStatus(
            envelope = envelope,
            balance = balance,
            withdrawnInPeriod = withdrawnInPeriod,
            available = allowed,
            lock = lock,
            period = period,
        )
    }

    /**
     * Figures of the envelope with [envelopeId] on [date], or `null` when it does not exist.
     */
    fun status(data: AppData, envelopeId: String, date: LocalDate): EnvelopeStatus? {
        val envelope = data.envelopes.firstOrNull { it.id == envelopeId } ?: return null
        val period = PeriodResolver.periodContaining(data.settings.period, date)
        return status(envelope, data.envelopeOperations, period, date)
    }

    /**
     * Total balance of the envelopes with the [EnvelopeRole.CUSHION] role, never negative, or
     * `null` when there are none.
     */
    fun cushionBalance(data: AppData): BigDecimal? {
        val ids = data.envelopes.filter { it.role == EnvelopeRole.CUSHION }.map { it.id }.toSet()
        if (ids.isEmpty()) return null
        return data.envelopeOperations
            .filter { it.envelopeId in ids }
            .fold(BigDecimal.ZERO) { total, operation ->
                when (operation.type) {
                    EnvelopeOperationType.DEPOSIT -> total + operation.amount
                    EnvelopeOperationType.WITHDRAWAL -> total - operation.amount
                }
            }
            .max(BigDecimal.ZERO)
    }

    /**
     * Figures of every envelope on [date].
     */
    fun summary(data: AppData, date: LocalDate): EnvelopesSummary {
        val period = PeriodResolver.periodContaining(data.settings.period, date)
        val statuses = data.envelopes.map { status(it, data.envelopeOperations, period, date) }
        return EnvelopesSummary(
            total = statuses.fold(BigDecimal.ZERO) { total, status -> total + status.balance },
            available = statuses.fold(BigDecimal.ZERO) { total, status -> total + status.available },
            statuses = statuses,
        )
    }
}

/**
 * Total balance of the envelopes with the [EnvelopeRole.SPENDING] role, none counted below zero,
 * or `null` when there are none.
 */
fun List<EnvelopeStatus>.spendingBalance(): BigDecimal? {
    val spending = filter { it.envelope.role == EnvelopeRole.SPENDING }
    if (spending.isEmpty()) return null
    return spending.fold(BigDecimal.ZERO) { total, status -> total + status.balance.max(BigDecimal.ZERO) }
}

/**
 * The cushion balance the budget starts from: the cushion envelopes when there are any, otherwise
 * the balance entered in the settings.
 */
fun AppData.effectiveCushionCurrent(): BigDecimal =
    EnvelopeCalculator.cushionBalance(this) ?: settings.cushionCurrent
