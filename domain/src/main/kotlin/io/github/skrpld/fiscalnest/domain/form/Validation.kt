/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.form

/**
 * Why a form field was rejected. The UI maps each value to a message.
 */
enum class FieldError {
    REQUIRED,
    INVALID_NUMBER,
    MUST_BE_POSITIVE,
    OUT_OF_RANGE,
    TOO_LONG,
    END_BEFORE_START,
    IN_THE_FUTURE,
    DUPLICATE,
}

/**
 * Outcome of validating a form whose fields are identified by [F].
 */
sealed interface Validation<out T, out F> {
    data class Valid<T>(val value: T) : Validation<T, Nothing>

    data class Invalid<F>(val errors: Map<F, FieldError>) : Validation<Nothing, F>
}

/**
 * Collects field errors while a form is validated.
 */
internal class ErrorCollector<F> {
    val errors: MutableMap<F, FieldError> = LinkedHashMap()

    fun add(field: F, error: FieldError) {
        errors.putIfAbsent(field, error)
    }

    fun <T> result(build: () -> T): Validation<T, F> =
        if (errors.isEmpty()) Validation.Valid(build()) else Validation.Invalid(errors.toMap())
}
