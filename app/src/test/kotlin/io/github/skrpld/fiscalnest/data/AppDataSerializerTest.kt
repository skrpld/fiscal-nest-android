/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.data

import androidx.datastore.core.CorruptionException
import io.github.skrpld.fiscalnest.domain.model.AppData
import io.github.skrpld.fiscalnest.sampleData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AppDataSerializerTest {
    private val serializer = AppDataSerializer(defaultValue = AppData())

    @Test
    fun `writes and reads the stored data`() = runTest {
        val output = ByteArrayOutputStream()
        serializer.writeTo(sampleData(), output)

        assertEquals(sampleData(), serializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun `reports corrupted files`() {
        assertThrows(CorruptionException::class.java) {
            kotlinx.coroutines.runBlocking { serializer.readFrom(ByteArrayInputStream("{broken".encodeToByteArray())) }
        }
    }
}
