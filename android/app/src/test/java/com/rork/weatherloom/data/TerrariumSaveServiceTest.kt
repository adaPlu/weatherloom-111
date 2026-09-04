package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.PlayerInventory
import com.rork.weatherloom.core.terrarium.QuantityMode
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumCategory
import com.rork.weatherloom.core.terrarium.TerrariumFootprint
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumPlacementRequest
import com.rork.weatherloom.core.terrarium.TerrariumRotation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrariumSaveServiceTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val rainbell = TerrariumItem(
        id = "rainbell",
        nameKey = "terrarium.item.rainbell",
        category = TerrariumCategory.Botanical,
        visualFamily = "bell-flower",
        footprint = TerrariumFootprint(width = 2, height = 1),
        allowedRotations = listOf(TerrariumRotation.Deg0, TerrariumRotation.Deg90),
        reactionTags = listOf("plant", "rain")
    )

    private val catalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = emptyList(),
        items = listOf(rainbell)
    )

    private val service = TerrariumSaveService(catalog)

    @Test
    fun batchOneGrantPlacePersistReloadProofPreservesInventoryAndLayout() {
        var persisted: String? = null
        val mutator = SaveStateMutator(SaveData()) { save ->
            persisted = json.encodeToString(SaveData.serializer(), save)
        }

        mutator.mutate { save ->
            service.grantItem(
                save = save,
                itemId = "rainbell",
                unlockSource = "batch1-proof"
            )
        }
        assertTrue(mutator.snapshot().terrariumInventory.owns("rainbell"))

        mutator.mutate { save ->
            service.placeItem(
                save = save,
                request = TerrariumPlacementRequest(
                    instanceId = "rainbell-001",
                    itemId = "rainbell",
                    xNormalized = 0.27f,
                    yNormalized = 0.71f,
                    rotation = TerrariumRotation.Deg90,
                    depthLayer = 3
                )
            )
        }

        val beforeExit = mutator.snapshot()
        val encoded = persisted ?: error("terrarium state was not persisted")
        val afterReturn = SaveMigration.decode(encoded, json)

        assertEquals(beforeExit.terrariumInventory, afterReturn.terrariumInventory)
        assertEquals(beforeExit.terrariumLayout, afterReturn.terrariumLayout)
        assertEquals(1, afterReturn.terrariumLayout.placements.size)
        val placement = afterReturn.terrariumLayout.placements.single()
        assertEquals("rainbell-001", placement.instanceId)
        assertEquals("rainbell", placement.itemId)
        assertEquals(0.27f, placement.xNormalized)
        assertEquals(0.71f, placement.yNormalized)
        assertEquals(TerrariumRotation.Deg90, placement.rotation)
        assertEquals(TerrariumFootprint(width = 1, height = 2), placement.logicalFootprint)
        assertEquals(3, placement.depthLayer)
    }

    @Test
    fun unlimitedUnlockGrantIsIdempotent() {
        val once = service.grantItem(
            save = SaveData(),
            itemId = "rainbell",
            unlockSource = "level-meadow-1"
        )
        val twice = service.grantItem(
            save = once,
            itemId = "rainbell",
            unlockSource = "level-meadow-1"
        )

        assertEquals(once, twice)
        assertEquals(1, twice.terrariumInventory.entries.size)
        assertEquals(QuantityMode.UnlimitedAfterUnlock, twice.terrariumInventory.entries.single().quantityMode)
    }

    @Test
    fun grantRejectsUnknownCatalogItem() {
        assertThrows(IllegalArgumentException::class.java) {
            service.grantItem(
                save = SaveData(),
                itemId = "missing-item",
                unlockSource = "test"
            )
        }
    }

    @Test
    fun placeRequiresPersistedOwnershipFromSameSave() {
        assertThrows(IllegalArgumentException::class.java) {
            service.placeItem(
                save = SaveData(terrariumInventory = PlayerInventory()),
                request = TerrariumPlacementRequest(
                    instanceId = "rainbell-001",
                    itemId = "rainbell",
                    xNormalized = 0.5f,
                    yNormalized = 0.5f
                )
            )
        }
    }

    @Test
    fun moveAndStoreChangeOnlyLayoutAndRemainSerializable() {
        val granted = service.grantItem(
            save = SaveData(),
            itemId = "rainbell",
            unlockSource = "test"
        )
        val placed = service.placeItem(
            save = granted,
            request = TerrariumPlacementRequest(
                instanceId = "rainbell-001",
                itemId = "rainbell",
                xNormalized = 0.2f,
                yNormalized = 0.3f
            )
        )
        val moved = service.moveItem(
            save = placed,
            instanceId = "rainbell-001",
            xNormalized = 0.8f,
            yNormalized = 0.6f,
            depthLayer = 4
        )

        assertEquals(granted.terrariumInventory, moved.terrariumInventory)
        assertEquals(0.8f, moved.terrariumLayout.placements.single().xNormalized)
        assertEquals(4, moved.terrariumLayout.placements.single().depthLayer)

        val restoredMoved = SaveMigration.decode(
            json.encodeToString(SaveData.serializer(), moved),
            json
        )
        assertEquals(moved.terrariumLayout, restoredMoved.terrariumLayout)

        val stored = service.storeItem(moved, "rainbell-001")
        assertTrue(stored.terrariumLayout.placements.isEmpty())
        assertTrue(stored.terrariumInventory.owns("rainbell"))

        val restoredStored = SaveMigration.decode(
            json.encodeToString(SaveData.serializer(), stored),
            json
        )
        assertEquals(stored.terrariumInventory, restoredStored.terrariumInventory)
        assertEquals(stored.terrariumLayout, restoredStored.terrariumLayout)
    }
}
