/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Inserts [event], or replaces the event with the same id in place.
 */
fun AppData.upsertEvent(event: BudgetEvent): AppData = copy(events = events.upsert(event) { it.id })

/**
 * Removes the event with [id] and its confirmation answers. Money already moved by confirmed
 * occurrences stays in the envelopes.
 */
fun AppData.deleteEvent(id: String): AppData = copy(
    events = events.filterNot { it.id == id },
    handledOccurrences = handledOccurrences.filterNot { it.eventId == id },
)

/**
 * Inserts [spending], or replaces the spending with the same id in place. Spending taken from an
 * envelope is mirrored by a withdrawal of that envelope, which is replaced or removed together
 * with the spending.
 */
fun AppData.upsertSpending(spending: Spending): AppData {
    val operationId = spendingOperationId(spending.id)
    val withoutOperation = envelopeOperations.filterNot { it.id == operationId }
    val envelopeId = spending.envelopeId
    val operations = if (envelopeId != null && envelopes.any { it.id == envelopeId }) {
        withoutOperation + EnvelopeOperation(
            id = operationId,
            envelopeId = envelopeId,
            type = EnvelopeOperationType.WITHDRAWAL,
            amount = spending.amount,
            date = spending.date,
            note = spending.note,
            source = OperationSource.FromSpending(spending.id),
        )
    } else {
        withoutOperation
    }
    return copy(spendings = spendings.upsert(spending) { it.id }, envelopeOperations = operations)
}

/**
 * Removes the spending with [id] together with the envelope withdrawal it caused, if any.
 */
fun AppData.deleteSpending(id: String): AppData {
    val operationId = spendingOperationId(id)
    return copy(
        spendings = spendings.filterNot { it.id == id },
        envelopeOperations = envelopeOperations.filterNot { it.id == operationId },
    )
}

/**
 * Starts the confirmation queue on [periodStart], usually the start of the current budget period,
 * unless it has started already. Occurrences dated earlier are history and never wait for
 * confirmation.
 */
fun AppData.startQueue(periodStart: LocalDate): AppData =
    if (queueStart != null) this else copy(queueStart = periodStart)

/**
 * Records that the occurrence of [event] on [date] happened: [amount] arrives in, or for an
 * expense is paid from, the envelope with [envelopeId]. Replaces an earlier answer.
 */
fun AppData.confirmOccurrence(event: BudgetEvent, date: LocalDate, envelopeId: String, amount: BigDecimal): AppData {
    val operation = EnvelopeOperation(
        id = occurrenceOperationId(event.id, date),
        envelopeId = envelopeId,
        type = if (event.kind == EventKind.INCOME) EnvelopeOperationType.DEPOSIT else EnvelopeOperationType.WITHDRAWAL,
        amount = amount,
        date = date,
        note = event.name,
        source = OperationSource.FromEvent(event.id, date),
    )
    return withAnswer(event.id, date, OccurrenceStatus.CONFIRMED)
        .copy(envelopeOperations = envelopeOperations.filterNot { it.id == operation.id } + operation)
}

/**
 * Records that the occurrence of the event with [eventId] on [date] did not happen. Replaces an
 * earlier answer.
 */
fun AppData.skipOccurrence(eventId: String, date: LocalDate): AppData {
    val operationId = occurrenceOperationId(eventId, date)
    return withAnswer(eventId, date, OccurrenceStatus.SKIPPED)
        .copy(envelopeOperations = envelopeOperations.filterNot { it.id == operationId })
}

/**
 * Forgets the answer for the occurrence of the event with [eventId] on [date], so that it waits
 * in the queue again, and takes back the money it moved.
 */
fun AppData.reopenOccurrence(eventId: String, date: LocalDate): AppData {
    val operationId = occurrenceOperationId(eventId, date)
    return copy(
        handledOccurrences = handledOccurrences.filterNot { it.eventId == eventId && it.date == date },
        envelopeOperations = envelopeOperations.filterNot { it.id == operationId },
    )
}

private fun AppData.withAnswer(eventId: String, date: LocalDate, status: OccurrenceStatus): AppData = copy(
    handledOccurrences = handledOccurrences.filterNot { it.eventId == eventId && it.date == date } +
        HandledOccurrence(eventId, date, status),
)

private fun spendingOperationId(spendingId: String): String = "spending-$spendingId"

private fun occurrenceOperationId(eventId: String, date: LocalDate): String = "event-$eventId-$date"

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
 * Inserts [envelope], or replaces the envelope with the same id in place.
 */
fun AppData.upsertEnvelope(envelope: Envelope): AppData = copy(envelopes = envelopes.upsert(envelope) { it.id })

/**
 * Removes the envelope with [id] together with its operations. Spending taken from it stays in
 * the log, and events paid through it stay scheduled, both without an envelope.
 */
fun AppData.deleteEnvelope(id: String): AppData = copy(
    envelopes = envelopes.filterNot { it.id == id },
    envelopeOperations = envelopeOperations.filterNot { it.envelopeId == id },
    spendings = spendings.map { if (it.envelopeId == id) it.copy(envelopeId = null) else it },
    events = events.map { if (it.envelopeId == id) it.copy(envelopeId = null) else it },
)

/**
 * Inserts [operation], or replaces the operation with the same id in place. An operation caused
 * by spending or by a confirmed event brings back what caused it, so that undoing [deleteEnvelopeOperation]
 * restores both.
 */
fun AppData.upsertEnvelopeOperation(operation: EnvelopeOperation): AppData {
    val withOperation = copy(envelopeOperations = envelopeOperations.upsert(operation) { it.id })
    return when (val source = operation.source) {
        null -> withOperation
        is OperationSource.FromSpending -> withOperation.copy(
            spendings = spendings.upsert(
                Spending(
                    id = source.spendingId,
                    amount = operation.amount,
                    date = operation.date,
                    note = operation.note,
                    envelopeId = operation.envelopeId,
                ),
            ) { it.id },
        )
        is OperationSource.FromEvent ->
            if (events.any { it.id == source.eventId }) {
                withOperation.withAnswer(source.eventId, source.date, OccurrenceStatus.CONFIRMED)
            } else {
                withOperation
            }
    }
}

/**
 * Removes the envelope operation with [id], if any. Removing the mirror of spending removes the
 * spending; removing a confirmed occurrence puts it back in the confirmation queue.
 */
fun AppData.deleteEnvelopeOperation(id: String): AppData {
    val operation = envelopeOperations.firstOrNull { it.id == id } ?: return this
    val without = copy(envelopeOperations = envelopeOperations.filterNot { it.id == id })
    return when (val source = operation.source) {
        null -> without
        is OperationSource.FromSpending -> without.copy(spendings = spendings.filterNot { it.id == source.spendingId })
        is OperationSource.FromEvent -> without.copy(
            handledOccurrences = handledOccurrences.filterNot { it.eventId == source.eventId && it.date == source.date },
        )
    }
}

/**
 * Marks the getting started guide as finished.
 */
fun AppData.completeOnboarding(): AppData = copy(onboardingCompleted = true)

/**
 * Replaces the cushion levels, sorted by fill threshold as the engine requires.
 */
fun BudgetSettings.withCushionLevels(levels: List<CushionLevel>): BudgetSettings =
    copy(cushionLevels = levels.sortedBy { it.maxFillRatio })

private inline fun <T> List<T>.upsert(item: T, key: (T) -> String): List<T> {
    val id = key(item)
    return if (any { key(it) == id }) map { if (key(it) == id) item else it } else this + item
}
