/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.domain.data

import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.domain.model.AppDataValidator
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * JSON format of [AppData], used for local storage and for backup files.
 *
 * Unknown keys are ignored and missing keys take their defaults, so older and newer app versions
 * can read each other's files as long as the schema version is supported.
 */
object AppDataCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prettyJson = Json(from = json) {
        prettyPrint = true
    }

    /** Encodes [data]; [pretty] adds indentation for human-readable backups. */
    fun encode(data: AppData, pretty: Boolean = false): String =
        (if (pretty) prettyJson else json).encodeToString(AppData.serializer(), data)

    /**
     * Decodes stored data without validating it.
     *
     * @throws SerializationException if [text] is not a valid document
     * @throws IllegalArgumentException if a value cannot be represented
     */
    fun decode(text: String): AppData = json.decodeFromString(AppData.serializer(), text)

    /**
     * Decodes and validates a backup file.
     *
     * @return the data, or a failure whose message describes the first problem found
     */
    fun decodeBackup(text: String): Result<AppData> {
        val data = try {
            decode(text)
        } catch (e: SerializationException) {
            return Result.failure(InvalidBackupException(e.message ?: "Malformed backup", e))
        } catch (e: IllegalArgumentException) {
            return Result.failure(InvalidBackupException(e.message ?: "Malformed backup", e))
        }
        val problems = AppDataValidator.problems(data)
        return if (problems.isEmpty()) Result.success(data) else Result.failure(InvalidBackupException(problems.first()))
    }
}

/**
 * Thrown when a backup file cannot be restored.
 */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
