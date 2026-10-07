/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.navigation

import kotlinx.serialization.Serializable

/** The top-level tabs. */
@Serializable
data object MainDestination

/**
 * The event editor.
 *
 * @property eventId event to edit, `null` to create one
 * @property kind name of the [io.github.skrpld.fiscalnest.domain.model.EventKind] preselected
 * for a new event
 */
@Serializable
data class EventEditorDestination(val eventId: String? = null, val kind: String? = null)

/** Details of the forecast period at [index], `0` being the current one. */
@Serializable
data class PeriodDetailDestination(val index: Int)

/** The full spending log. */
@Serializable
data object SpendingsDestination

/** Editor of the cushion criticality levels. */
@Serializable
data object CushionLevelsDestination

/** Details and history of the envelope with [envelopeId]. */
@Serializable
data class EnvelopeDetailDestination(val envelopeId: String)

/**
 * The envelope editor.
 *
 * @property envelopeId envelope to edit, `null` to create one
 */
@Serializable
data class EnvelopeEditorDestination(val envelopeId: String? = null)

/** The getting started guide, opened again from the settings. */
@Serializable
data object OnboardingDestination
