package com.rork.weatherloom.core.terrarium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TerrariumDomainModelTest {

    private fun rainbell(
        rotations: List<TerrariumRotation> = listOf(TerrariumRotation.Deg0),
        profileId: String? = "botanical-five-stage"
    ) = TerrariumItem(
        id = "rainbell",
        nameKey = "terrarium.item.rainbell",
        category = TerrariumCategory.Botanical,
        visualFamily = "bell-flower",
        footprint = TerrariumFootprint(1, 1),
        allowedRotations = rotations,
        reactionTags = listOf("plant", "rainbell", "rain"),
        growthProfileId = profileId,
        assetRef = "spec_rainbell"
    )

    private fun plantProfile() = GrowthProfile(
        id = "botanical-five-stage",
        stages = listOf(
            GrowthStage.Seed,
            GrowthStage.Sprout,
            GrowthStage.Young,
            GrowthStage.Mature,
            GrowthStage.Bloom
        ),
        pulsesPerStage = 1
    )

    private fun catalog(vararg items: TerrariumItem): TerrariumCatalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = listOf(plantProfile()),
        items = items.toList()
    )

    @Test
    fun footprintMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            TerrariumFootprint(width = 0, height = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerrariumFootprint(width = 1, height = -1)
        }
    }

    @Test
    fun footprintRotationIsLogicalAndDeterministic() {
        val footprint = TerrariumFootprint(width = 2, height = 1)
        assertEquals(TerrariumFootprint(2, 1), footprint.rotated(TerrariumRotation.Deg0))
        assertEquals(TerrariumFootprint(1, 2), footprint.rotated(TerrariumRotation.Deg90))
        assertEquals(TerrariumFootprint(2, 1), footprint.rotated(TerrariumRotation.Deg180))
        assertEquals(TerrariumFootprint(1, 2), footprint.rotated(TerrariumRotation.Deg270))
    }

    @Test
    fun catalogRejectsDuplicateStableItemIds() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(rainbell(), rainbell())
        }
    }

    @Test
    fun catalogRejectsMissingGrowthProfileReference() {
        assertThrows(IllegalArgumentException::class.java) {
            TerrariumCatalog(
                schemaVersion = 1,
                growthProfiles = listOf(plantProfile()),
                items = listOf(rainbell(profileId = "missing-profile"))
            )
        }
    }

    @Test
    fun itemRequiresAtLeastOneUniqueRotation() {
        assertThrows(IllegalArgumentException::class.java) {
            rainbell(rotations = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            rainbell(rotations = listOf(TerrariumRotation.Deg0, TerrariumRotation.Deg0))
        }
    }

    @Test
    fun shippedCatalogContainsEveryLegacyCollectibleId() {
        val candidates = listOf(
            File("src/main/assets/terrarium_items.json"),
            File("app/src/main/assets/terrarium_items.json")
        )
        val asset = candidates.firstOrNull { it.isFile }
            ?: error("terrarium_items.json not found from ${File(".").absolutePath}")
        val decoded = TerrariumCatalog.decode(asset.readText())
        val expected = setOf(
            "sunlace",
            "cloudmoss",
            "windreed",
            "mistcap",
            "frostfern",
            "rainbell",
            "thawlily",
            "galeflax",
            "loomstar"
        )

        assertEquals(1, decoded.schemaVersion)
        assertTrue(decoded.items.map { it.id }.toSet().containsAll(expected))
        assertEquals(expected.size, expected.count { decoded.item(it) != null })
    }

    @Test
    fun inventoryOwnershipIsSeparateFromPlacement() {
        val inventory = PlayerInventory(
            entries = listOf(
                InventoryEntry(
                    itemId = "rainbell",
                    quantityMode = QuantityMode.UnlimitedAfterUnlock,
                    quantity = 1,
                    unlockSource = "c5-1"
                )
            )
        )
        val layout = TerrariumLayout()

        assertTrue(inventory.owns("rainbell"))
        assertTrue(layout.placements.isEmpty())
    }

    @Test
    fun inventoryRejectsDuplicateOwnershipRows() {
        val entry = InventoryEntry(itemId = "rainbell")
        assertThrows(IllegalArgumentException::class.java) {
            PlayerInventory(entries = listOf(entry, entry))
        }
    }

    @Test
    fun layoutInstanceIdIsIndependentFromItemId() {
        val first = placement(instanceId = "plant-001", itemId = "rainbell")
        val second = placement(instanceId = "plant-002", itemId = "rainbell", x = 0.7f)
        val layout = TerrariumLayout(listOf(first, second))

        assertNotEquals(layout.placements[0].instanceId, layout.placements[0].itemId)
        assertEquals("rainbell", layout.placements[1].itemId)
        assertEquals(2, layout.placements.map { it.instanceId }.toSet().size)
    }

    @Test
    fun layoutRejectsDuplicateInstanceIds() {
        assertThrows(IllegalArgumentException::class.java) {
            TerrariumLayout(
                listOf(
                    placement(instanceId = "plant-001", itemId = "rainbell"),
                    placement(instanceId = "plant-001", itemId = "cloudmoss", x = 0.7f)
                )
            )
        }
    }

    @Test
    fun normalizedPositionsCannotLeavePlacementSurface() {
        assertThrows(IllegalArgumentException::class.java) {
            placement(instanceId = "bad-x", itemId = "rainbell", x = -0.01f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            placement(instanceId = "bad-y", itemId = "rainbell", y = 1.01f)
        }
    }

    @Test
    fun catalogRejectsImpossiblePlacementRotation() {
        val catalog = catalog(rainbell(rotations = listOf(TerrariumRotation.Deg0)))
        val layout = TerrariumLayout(
            listOf(
                placement(
                    instanceId = "plant-001",
                    itemId = "rainbell",
                    rotation = TerrariumRotation.Deg90
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            catalog.requireValid(layout)
        }
    }

    @Test
    fun catalogRejectsPlacementFootprintDrift() {
        val catalog = catalog(rainbell())
        val layout = TerrariumLayout(
            listOf(
                placement(
                    instanceId = "plant-001",
                    itemId = "rainbell",
                    footprint = TerrariumFootprint(2, 2)
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            catalog.requireValid(layout)
        }
    }

    @Test
    fun catalogRejectsUnknownPlacementItem() {
        val catalog = catalog(rainbell())
        val layout = TerrariumLayout(
            listOf(placement(instanceId = "plant-001", itemId = "deleted-item"))
        )

        assertThrows(IllegalArgumentException::class.java) {
            catalog.requireValid(layout)
        }
    }

    @Test
    fun growthStateIsIndependentFromLayoutMovement() {
        val growth = GrowthState(
            instanceId = "plant-001",
            growthProfileId = "botanical-five-stage",
            stageIndex = 2,
            growthPulsesApplied = 7,
            lastRelevantEchoId = "echo-rain-1"
        )
        val before = placement(instanceId = "plant-001", itemId = "rainbell", x = 0.2f)
        val after = before.copy(xNormalized = 0.8f, depthLayer = 2)

        assertNotEquals(before, after)
        assertEquals(2, growth.stageIndex)
        assertEquals(7, growth.growthPulsesApplied)
        assertEquals("plant-001", growth.instanceId)
    }

    @Test
    fun growthStateRejectsNegativeProgress() {
        assertThrows(IllegalArgumentException::class.java) {
            GrowthState(
                instanceId = "plant-001",
                growthProfileId = "botanical-five-stage",
                stageIndex = -1
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GrowthState(
                instanceId = "plant-001",
                growthProfileId = "botanical-five-stage",
                growthPulsesApplied = -1
            )
        }
    }

    @Test
    fun catalogRejectsGrowthStageOutsideProfile() {
        val catalog = catalog(rainbell())
        val growth = GrowthState(
            instanceId = "plant-001",
            growthProfileId = "botanical-five-stage",
            stageIndex = 99
        )

        assertThrows(IllegalArgumentException::class.java) {
            catalog.requireValid(growth)
        }
    }

    private fun placement(
        instanceId: String,
        itemId: String,
        x: Float = 0.3f,
        y: Float = 0.6f,
        rotation: TerrariumRotation = TerrariumRotation.Deg0,
        footprint: TerrariumFootprint = TerrariumFootprint(1, 1)
    ) = TerrariumPlacement(
        instanceId = instanceId,
        itemId = itemId,
        variantId = "default",
        xNormalized = x,
        yNormalized = y,
        rotation = rotation,
        logicalFootprint = footprint,
        depthLayer = 1
    )
}
