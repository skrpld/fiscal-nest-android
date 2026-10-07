/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.envelopes

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.skrpld.fiscalnest.R
import io.github.skrpld.fiscalnest.domain.envelope.EnvelopeLock
import io.github.skrpld.fiscalnest.domain.form.EnvelopePolicyType
import io.github.skrpld.fiscalnest.domain.model.Envelope
import io.github.skrpld.fiscalnest.domain.model.EnvelopePolicy
import io.github.skrpld.fiscalnest.ui.common.LocalDateTexts
import io.github.skrpld.fiscalnest.ui.common.LocalMoneyFormatter

@StringRes
fun EnvelopePolicyType.labelRes(): Int = when (this) {
    EnvelopePolicyType.FLEXIBLE -> R.string.envelope_policy_flexible
    EnvelopePolicyType.PERIOD_LIMIT -> R.string.envelope_policy_period_limit
    EnvelopePolicyType.LOCKED_UNTIL -> R.string.envelope_policy_locked
    EnvelopePolicyType.UNTIL_TARGET -> R.string.envelope_policy_goal
}

@StringRes
fun EnvelopePolicyType.hintRes(): Int = when (this) {
    EnvelopePolicyType.FLEXIBLE -> R.string.envelope_policy_flexible_hint
    EnvelopePolicyType.PERIOD_LIMIT -> R.string.envelope_policy_period_limit_hint
    EnvelopePolicyType.LOCKED_UNTIL -> R.string.envelope_policy_locked_hint
    EnvelopePolicyType.UNTIL_TARGET -> R.string.envelope_policy_goal_hint
}

val EnvelopePolicy.icon: ImageVector
    get() = when (this) {
        EnvelopePolicy.Flexible -> Icons.Rounded.LockOpen
        is EnvelopePolicy.PeriodLimit -> Icons.Rounded.Speed
        is EnvelopePolicy.LockedUntil -> Icons.Rounded.Lock
        EnvelopePolicy.UntilTarget -> Icons.Rounded.Flag
    }

/** The spending rule of [envelope] in one line: `Up to 5 000 ₽ per period`. */
@Composable
fun envelopePolicyText(envelope: Envelope): String {
    val money = LocalMoneyFormatter.current
    return when (val policy = envelope.policy) {
        EnvelopePolicy.Flexible -> stringResource(R.string.envelope_policy_flexible)
        is EnvelopePolicy.PeriodLimit -> stringResource(R.string.envelope_policy_period_limit_summary, money.format(policy.limit))
        is EnvelopePolicy.LockedUntil ->
            stringResource(R.string.envelope_policy_locked_summary, LocalDateTexts.current.date(policy.date))
        EnvelopePolicy.UntilTarget -> {
            val target = envelope.target
            if (target == null) {
                stringResource(R.string.envelope_policy_goal)
            } else {
                stringResource(R.string.envelope_policy_goal_summary, money.format(target))
            }
        }
    }
}

/** Why money is held back, or `null` when nothing is. */
@Composable
fun envelopeLockText(lock: EnvelopeLock?): String? = when (lock) {
    null -> null
    is EnvelopeLock.UntilDate -> stringResource(R.string.envelope_lock_date, LocalDateTexts.current.date(lock.date))
    is EnvelopeLock.UntilTarget -> stringResource(R.string.envelope_lock_goal, LocalMoneyFormatter.current.format(lock.missing))
    EnvelopeLock.PeriodLimitReached -> stringResource(R.string.envelope_lock_limit)
}
