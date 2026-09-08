package com.rork.weatherloom.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveRecoveryTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun corruptPayloadIsQuarantinedUntilExplicitRecovery() {
        val raw = "{ definitely-not-json"
        val loaded = SaveMigration.load(raw, json)

        assertEquals(SaveData(), loaded.save)
        assertTrue(loaded.recoveryRequired)
        assertEquals(raw, loaded.recoveryRaw)
        assertFalse(loaded.writable)

        var persistedRaw = raw
        var writes = 0
        val mutator = SaveStateMutator(
            initial = loaded.save,
            writable = loaded.writable
        ) { next ->
            writes++
            persistedRaw = json.encodeToString(SaveData.serializer(), next)
        }

        mutator.mutate { current -> current.copy(tutorialSeen = true) }

        assertEquals(0, writes)
        assertEquals(raw, persistedRaw)

        mutator.recover(SaveData(tutorialSeen = true))

        assertEquals(1, writes)
        assertTrue(SaveMigration.decode(persistedRaw, json).tutorialSeen)
    }
}
