package com.rork.weatherloom.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TerrariumSaveMigrationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun schemaTwoCollectiblesBootstrapTerrariumInventoryWithoutLosingUnlockHistory() {
        val raw = """
            {
              "schema": 2,
              "collectibles": ["rainbell", "cloudmoss", "rainbell", "loomstar"],
              "lastCollectible": "loomstar",
              "tutorialSeen": true
            }
        """.trimIndent()

        val migrated = SaveMigration.decode(raw, json)
        val encoded = json.encodeToJsonElement(SaveData.serializer(), migrated).jsonObject

        assertEquals(3, migrated.schema)
        assertEquals(listOf("rainbell", "cloudmoss", "loomstar"), migrated.collectibles)
        assertEquals("loomstar", migrated.lastCollectible)
        assertEquals(true, migrated.tutorialSeen)

        val inventoryElement = encoded["terrariumInventory"]
        assertNotNull("schema-3 save must serialize terrariumInventory", inventoryElement)
        val entries = inventoryElement!!.jsonObject.getValue("entries").jsonArray
        assertEquals(
            listOf("rainbell", "cloudmoss", "loomstar"),
            entries.map { it.jsonObject.getValue("itemId").jsonPrimitive.content }
        )
        assertEquals(
            listOf("legacy_collectible", "legacy_collectible", "legacy_collectible"),
            entries.map { it.jsonObject.getValue("unlockSource").jsonPrimitive.content }
        )
    }

    @Test
    fun schemaThreeTerrariumStateRoundTripsIdempotently() {
        val raw = """
            {
              "schema": 3,
              "collectibles": ["rainbell"],
              "terrariumInventory": {
                "entries": [
                  {
                    "itemId": "rainbell",
                    "variantId": "default",
                    "quantityMode": "UnlimitedAfterUnlock",
                    "quantity": 1,
                    "unlockSource": "legacy_collectible"
                  }
                ]
              },
              "terrariumLayout": {
                "placements": [
                  {
                    "instanceId": "legacy-rainbell",
                    "itemId": "rainbell",
                    "variantId": "default",
                    "xNormalized": 0.3,
                    "yNormalized": 0.6,
                    "rotation": "Deg0",
                    "logicalFootprint": {"width": 1, "height": 1},
                    "depthLayer": 1
                  }
                ]
              },
              "terrariumGrowth": [
                {
                  "instanceId": "legacy-rainbell",
                  "growthProfileId": "botanical-five-stage",
                  "stageIndex": 2,
                  "growthPulsesApplied": 7,
                  "lastRelevantEchoId": "echo-rain-1"
                }
              ]
            }
        """.trimIndent()

        val once = SaveMigration.decode(raw, json)
        val onceEncoded = json.encodeToString(SaveData.serializer(), once)
        val twice = SaveMigration.decode(onceEncoded, json)
        val twiceEncoded = json.encodeToJsonElement(SaveData.serializer(), twice).jsonObject

        assertEquals(3, twice.schema)
        assertEquals(once, twice)
        assertEquals(
            "rainbell",
            twiceEncoded.getValue("terrariumInventory")
                .jsonObject.getValue("entries").jsonArray.single()
                .jsonObject.getValue("itemId").jsonPrimitive.content
        )
        assertEquals(
            "legacy-rainbell",
            twiceEncoded.getValue("terrariumLayout")
                .jsonObject.getValue("placements").jsonArray.single()
                .jsonObject.getValue("instanceId").jsonPrimitive.content
        )
        assertEquals(
            7,
            twiceEncoded.getValue("terrariumGrowth")
                .jsonArray.single().jsonObject.getValue("growthPulsesApplied").jsonPrimitive.content.toInt()
        )
    }
}
