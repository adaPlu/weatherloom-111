package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumCategory
import com.rork.weatherloom.core.terrarium.TerrariumFootprint
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class PuzzleSolveServiceTest {

    private val catalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = emptyList(),
        items = listOf(
            TerrariumItem(
                id = "sunlace",
                nameKey = "terrarium.item.sunlace",
                category = TerrariumCategory.Botanical,
                visualFamily = "sun-lace",
                footprint = TerrariumFootprint(width = 1, height = 1),
                allowedRotations = listOf(TerrariumRotation.Deg0),
                reactionTags = listOf("plant", "sunlace")
            )
        )
    )

    @Test
    fun solvedPuzzleUpdatesProgressXpCollectibleAndTerrariumInventoryAtomically() {
        val service = PuzzleSolveService(PuzzleRewardBridge(catalog))

        val result = service.recordSolve(
            save = SaveData(),
            levelId = "c1-1",
            rating = Rating.Seedling,
            strokes = 3,
            cells = 14,
            rewardId = "sunlace"
        )

        assertEquals(Rating.Seedling, result.save.levels.getValue("c1-1").ratingEnum)
        assertEquals(100, result.save.playerProgression.xp)
        assertEquals(100, result.save.playerProgression.awardedLevelXp.getValue("c1-1"))
        assertEquals(100, result.xpGranted)
        assertEquals(listOf("sunlace"), result.save.collectibles)
        assertEquals("sunlace", result.save.lastCollectible)
        assertTrue(result.save.terrariumInventory.owns("sunlace"))
        assertTrue(result.newlyUnlockedCollectible)
        assertTrue(result.newTerrariumItem)
    }

    @Test
    fun solveReplayCannotFarmXpAndBetterRatingsGrantOnlyTheirDifference() {
        val service = PuzzleSolveService(PuzzleRewardBridge(catalog))
        val first = service.recordSolve(
            save = SaveData(),
            levelId = "c1-1",
            rating = Rating.Seedling,
            strokes = 4,
            cells = 18,
            rewardId = "sunlace"
        )

        val repeated = service.recordSolve(
            save = first.save,
            levelId = "c1-1",
            rating = Rating.Seedling,
            strokes = 4,
            cells = 18,
            rewardId = "sunlace"
        )
        assertEquals(100, repeated.save.playerProgression.xp)
        assertEquals(0, repeated.xpGranted)

        val bloom = service.recordSolve(
            save = repeated.save,
            levelId = "c1-1",
            rating = Rating.Bloom,
            strokes = 3,
            cells = 15,
            rewardId = "sunlace"
        )
        assertEquals(150, bloom.save.playerProgression.xp)
        assertEquals(50, bloom.xpGranted)

        val lowerReplay = service.recordSolve(
            save = bloom.save,
            levelId = "c1-1",
            rating = Rating.Seedling,
            strokes = 5,
            cells = 20,
            rewardId = "sunlace"
        )
        assertEquals(150, lowerReplay.save.playerProgression.xp)
        assertEquals(0, lowerReplay.xpGranted)

        val flourish = service.recordSolve(
            save = lowerReplay.save,
            levelId = "c1-1",
            rating = Rating.Flourish,
            strokes = 2,
            cells = 12,
            rewardId = "sunlace"
        )
        assertEquals(200, flourish.save.playerProgression.xp)
        assertEquals(50, flourish.xpGranted)
        assertEquals(Rating.Flourish, flourish.save.levels.getValue("c1-1").ratingEnum)
    }

    @Test
    fun simultaneousReplaysThroughSerializedPersistenceCannotDoubleAwardXp() {
        val service = PuzzleSolveService(PuzzleRewardBridge(catalog))
        val workers = 8
        val start = CountDownLatch(1)
        val finished = CountDownLatch(workers)
        val mutator = SaveStateMutator(SaveData()) { }

        repeat(workers) {
            thread(start = true) {
                start.await()
                mutator.mutateWithResult { current ->
                    val result = service.recordSolve(
                        save = current,
                        levelId = "c1-1",
                        rating = Rating.Seedling,
                        strokes = 4,
                        cells = 18,
                        rewardId = "sunlace"
                    )
                    result.save to result.xpGranted
                }
                finished.countDown()
            }
        }

        start.countDown()
        assertTrue("workers did not finish", finished.await(5, TimeUnit.SECONDS))

        val finalSave = mutator.snapshot()
        assertEquals(100, finalSave.playerProgression.xp)
        assertEquals(100, finalSave.playerProgression.awardedLevelXp.getValue("c1-1"))
        assertEquals(Rating.Seedling, finalSave.levels.getValue("c1-1").ratingEnum)
        assertEquals(listOf("sunlace"), finalSave.collectibles)
        assertTrue(finalSave.terrariumInventory.owns("sunlace"))
    }
}
