package com.rork.weatherloom.core.terrarium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrariumPlacementServiceTest {

    private val item = TerrariumItem(
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
        items = listOf(item)
    )

    private val service = TerrariumPlacementService(catalog)

    @Test
    fun ownedUnlimitedItemCanBePlacedAndFootprintComesFromCatalog() {
        val inventory = PlayerInventory(
            listOf(
                InventoryEntry(
                    itemId = "rainbell",
                    quantityMode = QuantityMode.UnlimitedAfterUnlock,
                    quantity = 1
                )
            )
        )

        val placed = service.place(
            layout = TerrariumLayout(),
            inventory = inventory,
            request = TerrariumPlacementRequest(
                instanceId = "rainbell-001",
                itemId = "rainbell",
                xNormalized = 0.25f,
                yNormalized = 0.75f,
                rotation = TerrariumRotation.Deg90,
                depthLayer = 2
            )
        )

        assertEquals(1, placed.placements.size)
        val placement = placed.placements.single()
        assertEquals("rainbell-001", placement.instanceId)
        assertEquals("rainbell", placement.itemId)
        assertEquals(TerrariumFootprint(width = 1, height = 2), placement.logicalFootprint)
        assertEquals(0.25f, placement.xNormalized)
        assertEquals(0.75f, placement.yNormalized)
        assertEquals(2, placement.depthLayer)
    }

    @Test
    fun placementRejectsUnownedItem() {
        assertThrows(IllegalArgumentException::class.java) {
            service.place(
                layout = TerrariumLayout(),
                inventory = PlayerInventory(),
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
    fun finiteInventoryCannotPlaceMoreInstancesThanOwnedQuantity() {
        val inventory = PlayerInventory(
            listOf(
                InventoryEntry(
                    itemId = "rainbell",
                    quantityMode = QuantityMode.Finite,
                    quantity = 1
                )
            )
        )
        val first = service.place(
            layout = TerrariumLayout(),
            inventory = inventory,
            request = TerrariumPlacementRequest(
                instanceId = "rainbell-001",
                itemId = "rainbell",
                xNormalized = 0.2f,
                yNormalized = 0.4f
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.place(
                layout = first,
                inventory = inventory,
                request = TerrariumPlacementRequest(
                    instanceId = "rainbell-002",
                    itemId = "rainbell",
                    xNormalized = 0.7f,
                    yNormalized = 0.4f
                )
            )
        }
    }

    @Test
    fun placementRejectsDuplicateInstanceId() {
        val inventory = PlayerInventory(listOf(InventoryEntry(itemId = "rainbell")))
        val first = service.place(
            layout = TerrariumLayout(),
            inventory = inventory,
            request = TerrariumPlacementRequest(
                instanceId = "same-id",
                itemId = "rainbell",
                xNormalized = 0.2f,
                yNormalized = 0.4f
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.place(
                layout = first,
                inventory = inventory,
                request = TerrariumPlacementRequest(
                    instanceId = "same-id",
                    itemId = "rainbell",
                    xNormalized = 0.8f,
                    yNormalized = 0.4f
                )
            )
        }
    }

    @Test
    fun moveChangesOnlyPlacementCoordinatesAndDepth() {
        val inventory = PlayerInventory(listOf(InventoryEntry(itemId = "rainbell")))
        val initial = service.place(
            layout = TerrariumLayout(),
            inventory = inventory,
            request = TerrariumPlacementRequest(
                instanceId = "rainbell-001",
                itemId = "rainbell",
                xNormalized = 0.2f,
                yNormalized = 0.3f,
                rotation = TerrariumRotation.Deg90,
                depthLayer = 1
            )
        )
        val before = initial.placements.single()

        val moved = service.move(
            layout = initial,
            instanceId = "rainbell-001",
            xNormalized = 0.8f,
            yNormalized = 0.6f,
            depthLayer = 4
        )
        val after = moved.placements.single()

        assertEquals(before.instanceId, after.instanceId)
        assertEquals(before.itemId, after.itemId)
        assertEquals(before.variantId, after.variantId)
        assertEquals(before.rotation, after.rotation)
        assertEquals(before.logicalFootprint, after.logicalFootprint)
        assertEquals(0.8f, after.xNormalized)
        assertEquals(0.6f, after.yNormalized)
        assertEquals(4, after.depthLayer)
    }

    @Test
    fun moveRejectsUnknownInstance() {
        assertThrows(IllegalArgumentException::class.java) {
            service.move(
                layout = TerrariumLayout(),
                instanceId = "missing",
                xNormalized = 0.5f,
                yNormalized = 0.5f,
                depthLayer = 0
            )
        }
    }

    @Test
    fun removeDeletesOnlyRequestedPlacement() {
        val inventory = PlayerInventory(listOf(InventoryEntry(itemId = "rainbell")))
        val first = service.place(
            layout = TerrariumLayout(),
            inventory = inventory,
            request = TerrariumPlacementRequest(
                instanceId = "rainbell-001",
                itemId = "rainbell",
                xNormalized = 0.2f,
                yNormalized = 0.3f
            )
        )
        val second = service.place(
            layout = first,
            inventory = inventory,
            request = TerrariumPlacementRequest(
                instanceId = "rainbell-002",
                itemId = "rainbell",
                xNormalized = 0.8f,
                yNormalized = 0.3f
            )
        )

        val removed = service.remove(second, "rainbell-001")

        assertEquals(1, removed.placements.size)
        assertEquals("rainbell-002", removed.placements.single().instanceId)
        assertTrue(inventory.owns("rainbell"))
    }
}
