/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import io.github.skrpld.fiscalnest.domain.data.AppDataCodec
import io.github.skrpld.fiscalnest.domain.data.BudgetRepository
import io.github.skrpld.fiscalnest.domain.model.AppData
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

private const val DATA_FILE_NAME = "fiscal_nest.json"

/**
 * Creates the store holding all app data. Must be called once per process for the file.
 *
 * @param defaultValue data used before anything has been saved
 */
fun createAppDataStore(context: Context, defaultValue: AppData): DataStore<AppData> = DataStoreFactory.create(
    serializer = AppDataSerializer(defaultValue),
    corruptionHandler = ReplaceFileCorruptionHandler { defaultValue },
    produceFile = { context.dataStoreFile(DATA_FILE_NAME) },
)

/**
 * Stores [AppData] as JSON in a single file.
 */
class AppDataSerializer(override val defaultValue: AppData) : Serializer<AppData> {
    override suspend fun readFrom(input: InputStream): AppData = try {
        AppDataCodec.decode(input.readBytes().decodeToString())
    } catch (e: IllegalArgumentException) {
        // Covers kotlinx.serialization.SerializationException, which extends it.
        throw CorruptionException("Cannot read the stored budget", e)
    }

    override suspend fun writeTo(t: AppData, output: OutputStream) {
        output.write(AppDataCodec.encode(t).encodeToByteArray())
    }
}

/**
 * [BudgetRepository] backed by DataStore: atomic, transactional writes off the main thread.
 */
class DataStoreBudgetRepository(private val store: DataStore<AppData>) : BudgetRepository {
    override val data: Flow<AppData> = store.data

    override suspend fun update(transform: (AppData) -> AppData) {
        store.updateData { transform(it) }
    }
}
