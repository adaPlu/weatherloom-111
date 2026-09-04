package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumCategory
import com.rork.weatherloom.core.terrarium.TerrariumFootprint
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumRotation
import org.junit.Assert.assertEquals
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
}
