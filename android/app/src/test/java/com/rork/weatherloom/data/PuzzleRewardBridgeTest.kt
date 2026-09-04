package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumCategory
import com.rork.weatherloom.core.terrarium.TerrariumFootprint
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleRewardBridgeTest {

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
    fun solvedLevelRewardGrantsMatchingTerrariumItem() {
        val bridge = PuzzleRewardBridge(catalog)

        val result = bridge.grant(
            save = SaveData(),
            levelId = "c1-1",
            rewardId = "sunlace"
        )

        assertTrue(result.save.terrariumInventory.owns("sunlace"))
        assertTrue(result.newTerrariumItem)
        assertEquals(
            "level:c1-1",
            result.save.terrariumInventory.entry("sunlace")?.unlockSource
        )
    }

    @Test
    fun repeatedRewardDoesNotDuplicateInventoryOrReplaceUnlockSource() {
        val bridge = PuzzleRewardBridge(catalog)
        val first = bridge.grant(
            save = SaveData(),
            levelId = "c1-1",
            rewardId = "sunlace"
        )

        val second = bridge.grant(
            save = first.save,
            levelId = "c1-9",
            rewardId = "sunlace"
        )

        assertFalse(second.newTerrariumItem)
        assertEquals(1, second.save.terrariumInventory.entries.size)
        assertEquals(
            "level:c1-1",
            second.save.terrariumInventory.entry("sunlace")?.unlockSource
        )
    }

    @Test
    fun missingOrDeprecatedRewardIdIsSafeNoOp() {
        val bridge = PuzzleRewardBridge(catalog)
        val original = SaveData(collectibles = listOf("legacy-keepsake"))

        val missing = bridge.grant(
            save = original,
            levelId = "c1-old",
            rewardId = "retired-item"
        )
        val absent = bridge.grant(
            save = original,
            levelId = "c1-none",
            rewardId = null
        )

        assertEquals(original, missing.save)
        assertFalse(missing.newTerrariumItem)
        assertEquals(null, missing.terrariumItemId)
        assertEquals(original, absent.save)
        assertFalse(absent.newTerrariumItem)
    }
}
