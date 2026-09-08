package com.rork.weatherloom.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavePersistenceTest {

    @Test
    fun failedWriteDoesNotAdvanceAuthoritativeState() {
        val initial = SaveData()
        val mutator = SaveStateMutator(initial) {
            throw IllegalStateException("simulated disk write failure")
        }

        val after = mutator.mutate { current ->
            current.copy(tutorialSeen = true)
        }

        assertEquals(initial, after)
        assertEquals(initial, mutator.snapshot())
    }

    @Test
    fun failedWriteDoesNotReportResultProducingMutationAsCommitted() {
        val initial = SaveData()
        val mutator = SaveStateMutator(initial) {
            throw IllegalStateException("simulated disk write failure")
        }

        val result = mutator.mutateWithResult { current ->
            current.copy(tutorialSeen = true) to "committed"
        }

        assertNull(result)
        assertEquals(initial, mutator.snapshot())
    }

    @Test
    fun unchangedResultDoesNotRequirePersistence() {
        val initial = SaveData()
        var writes = 0
        val mutator = SaveStateMutator(initial) {
            writes++
            throw IllegalStateException("no write should have been attempted")
        }

        val result = mutator.mutateWithResult { current ->
            current to "unchanged"
        }

        assertEquals("unchanged", result)
        assertEquals(0, writes)
        assertEquals(initial, mutator.snapshot())
    }

    @Test
    fun readOnlySaveCannotReportUnpersistedResultAsCommitted() {
        val initial = SaveData(schema = CURRENT_SAVE_SCHEMA + 1)
        val mutator = SaveStateMutator(
            initial = initial,
            writable = false
        ) {
            throw IllegalStateException("read-only save must not persist")
        }

        val result = mutator.mutateWithResult { current ->
            current.copy(tutorialSeen = true) to true
        }

        assertNull(result)
        assertEquals(initial, mutator.snapshot())
    }

    @Test
    fun failedRecoveryDoesNotClearQuarantineInMemory() {
        val initial = SaveData()
        val replacement = SaveData(tutorialSeen = true)
        val mutator = SaveStateMutator(
            initial = initial,
            writable = false
        ) {
            throw IllegalStateException("simulated recovery write failure")
        }

        val after = mutator.recover(replacement)

        assertEquals(initial, after)
        assertEquals(initial, mutator.snapshot())
    }

    @Test
    fun repositoryUsesObservableSynchronousWriteBeforePublishingState() {
        val source = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt"
        ).readText()
        val persistBlock = source
            .substringAfter("private fun persist(data: SaveData)")
            .substringBefore("\n    fun recordAttempt")

        assertFalse("apply() cannot report durable write failure", persistBlock.contains(".apply()"))
        assertFalse("persistence failure must reach the mutation gate", persistBlock.contains("runCatching"))
        assertTrue("persist must use an observable synchronous commit", persistBlock.contains(".commit()"))
        assertTrue("commit result must be checked", persistBlock.contains("val committed ="))
        assertTrue("failed commit must be rejected", persistBlock.contains("if (!committed)"))

        val commitIndex = persistBlock.indexOf(".commit()")
        val publishIndex = persistBlock.indexOf("_save.value = data")
        assertTrue("save flow must publish only after durable commit", publishIndex > commitIndex)
    }

    private fun repoFile(relative: String): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val base = current ?: return@repeat
            val candidate = File(base, relative)
            if (candidate.isFile) return candidate
            current = base.parentFile
        }
        error("Could not locate repository file: $relative from ${System.getProperty("user.dir")}")
    }
}
