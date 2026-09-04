package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumCategory
import com.rork.weatherloom.core.terrarium.TerrariumFootprint
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun solvedPuzzleUpdatesProgressCollectibleAndTerrariumInventoryAtomically() {
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
        assertEquals(listOf("sunlace"), result.save.collectibles)
        assertEquals("sunlace", result.save.lastCollectible)
        assertTrue(result.save.terrariumInventory.owns("sunlace"))
        assertTrue(result.newlyUnlockedCollectible)
        assertTrue(result.newTerrariumItem)
    }
}
