package com.rork.weatherloom.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SaveMigrationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun nullSaveReturnsCurrentDefaults() {
        assertEquals(SaveData(), SaveMigration.decode(null, json))
        assertEquals(CURRENT_SAVE_SCHEMA, SaveMigration.decode(null, json).schema)
    }

    @Test
    fun schemaOneMigratesWithoutDroppingProgressOrSettings() {
        val raw = """
            {
              "schema": 1,
              "levels": {
                "meadow-1": {
                  "rating": 3,
                  "attempts": 7,
                  "bestStrokes": 2,
                  "bestCells": 8
                }
              },
              "collectibles": ["rainbell", "cloudmoss"],
              "dailyHistory": ["2026-09-01", "2026-09-02"],
              "lastCollectible": "cloudmoss",
              "tutorialSeen": true,
              "reducedMotion": true,
              "highContrast": true,
              "musicEnabled": false,
              "soundEnabled": false
            }
        """.trimIndent()

        val migrated = SaveMigration.decode(raw, json)

        assertEquals(CURRENT_SAVE_SCHEMA, migrated.schema)
        assertEquals(3, migrated.levels.getValue("meadow-1").rating)
        assertEquals(7, migrated.levels.getValue("meadow-1").attempts)
        assertEquals(listOf("rainbell", "cloudmoss"), migrated.collectibles)
        assertEquals(listOf("2026-09-01", "2026-09-02"), migrated.dailyHistory)
        assertEquals("cloudmoss", migrated.lastCollectible)
        assertTrue(migrated.tutorialSeen)
        assertTrue(migrated.reducedMotion)
        assertTrue(migrated.highContrast)
        assertFalse(migrated.musicEnabled)
        assertFalse(migrated.soundEnabled)
    }

    @Test
    fun malformedLegacyRatingsAndCountersAreCanonicalized() {
        val raw = """
            {
              "schema": 1,
              "levels": {
                "negative": {
                  "rating": -4,
                  "attempts": -9,
                  "bestStrokes": -2,
                  "bestCells": -5
                },
                "too-high": {
                  "rating": 99,
                  "attempts": 3,
                  "bestStrokes": 2,
                  "bestCells": 8
                }
              }
            }
        """.trimIndent()

        val migrated = SaveMigration.decode(raw, json)

        assertEquals(0, migrated.levels.getValue("negative").rating)
        assertEquals(0, migrated.levels.getValue("negative").attempts)
        assertEquals(0, migrated.levels.getValue("negative").bestStrokes)
        assertEquals(0, migrated.levels.getValue("negative").bestCells)
        assertEquals(3, migrated.levels.getValue("too-high").rating)
    }

    @Test
    fun duplicateLegacyIdsAreDeduplicatedWithoutReordering() {
        val raw = """
            {
              "schema": 1,
              "collectibles": ["rainbell", "cloudmoss", "rainbell", "frostfern", "cloudmoss"],
              "dailyHistory": ["2026-09-01", "2026-09-01", "2026-09-02"]
            }
        """.trimIndent()

        val migrated = SaveMigration.decode(raw, json)

        assertEquals(listOf("rainbell", "cloudmoss", "frostfern"), migrated.collectibles)
        assertEquals(listOf("2026-09-01", "2026-09-02"), migrated.dailyHistory)
    }

    @Test
    fun currentSchemaDecodeIsIdempotent() {
        val expected = SaveData(
            levels = mapOf("ridge" to LevelRecord(rating = 2, attempts = 4, bestStrokes = 2, bestCells = 9)),
            collectibles = listOf("frostfern"),
            dailyHistory = listOf("2026-09-03"),
            tutorialSeen = true,
            reducedMotion = true
        )
        val raw = json.encodeToString(SaveData.serializer(), expected)

        val once = SaveMigration.decode(raw, json)
        val twice = SaveMigration.decode(
            json.encodeToString(SaveData.serializer(), once),
            json
        )

        assertEquals(expected, once)
        assertEquals(once, twice)
    }

    @Test
    fun unknownFieldsDoNotInvalidateKnownSaveData() {
        val raw = """
            {
              "schema": 2,
              "collectibles": ["rainbell"],
              "futureField": {"nested": true},
              "musicEnabled": false
            }
        """.trimIndent()

        val decoded = SaveMigration.decode(raw, json)

        assertEquals(listOf("rainbell"), decoded.collectibles)
        assertFalse(decoded.musicEnabled)
        assertEquals(CURRENT_SAVE_SCHEMA, decoded.schema)
    }

    @Test
    fun corruptPayloadFallsBackToFreshCurrentSave() {
        assertEquals(SaveData(), SaveMigration.decode("{ definitely-not-json", json))
    }

    @Test
    fun futureSchemaPreservesKnownFieldsAndSchemaMarker() {
        val raw = """
            {
              "schema": 99,
              "collectibles": ["rainbell"],
              "tutorialSeen": true,
              "unknownFutureState": [1, 2, 3]
            }
        """.trimIndent()

        val decoded = SaveMigration.decode(raw, json)

        assertEquals(99, decoded.schema)
        assertEquals(listOf("rainbell"), decoded.collectibles)
        assertTrue(decoded.tutorialSeen)
    }

    @Test
    fun serializedMutationsCannotLoseConcurrentUpdates() {
        val workers = 8
        val incrementsPerWorker = 250
        val start = CountDownLatch(1)
        val finished = CountDownLatch(workers)
        val mutator = SaveStateMutator(SaveData()) { }

        repeat(workers) {
            thread(start = true) {
                start.await()
                repeat(incrementsPerWorker) {
                    mutator.mutate { current ->
                        val rec = current.levels["counter"] ?: LevelRecord()
                        current.copy(
                            levels = current.levels +
                                ("counter" to rec.copy(attempts = rec.attempts + 1))
                        )
                    }
                }
                finished.countDown()
            }
        }

        start.countDown()
        assertTrue("workers did not finish", finished.await(5, TimeUnit.SECONDS))
        assertEquals(
            workers * incrementsPerWorker,
            mutator.snapshot().levels.getValue("counter").attempts
        )
    }

    @Test
    fun unchangedMutationDoesNotPersistAgain() {
        var writes = 0
        val mutator = SaveStateMutator(SaveData()) { writes++ }

        mutator.mutate { it }
        assertEquals(0, writes)

        mutator.mutate { it.copy(tutorialSeen = true) }
        assertEquals(1, writes)

        mutator.mutate { it.copy(tutorialSeen = true) }
        assertEquals(1, writes)
    }
}
