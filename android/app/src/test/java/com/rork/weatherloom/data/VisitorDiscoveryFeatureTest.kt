package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.GrowthProfile
import com.rork.weatherloom.core.terrarium.GrowthStage
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumCategory
import com.rork.weatherloom.core.terrarium.TerrariumFootprint
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumLayout
import com.rork.weatherloom.core.terrarium.TerrariumPlacement
import com.rork.weatherloom.core.terrarium.TerrariumRotation
import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.terrarium.reaction.ReactionCatalog
import com.rork.weatherloom.core.terrarium.reaction.ReactionResult
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VisitorDiscoveryFeatureTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val catalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = listOf(
            GrowthProfile(
                id = "botanical",
                stages = listOf(GrowthStage.Seed, GrowthStage.Sprout, GrowthStage.Bloom),
                pulsesPerStage = 1
            )
        ),
        items = listOf(rainbell())
    )

    @Test
    fun rainyRainbellEmitsRecomputableButterflyAndPersistsDiscoveryExactlyOnce() {
        val service = TerrariumReactionSaveService(catalog, visitorReactionCatalog())

        val first = service.evaluateAndApply(rainyRainbellSave("echo-visitor-1"))
        assertEquals(
            listOf("butterfly" to "rainbell-001"),
            visitorPairs(first.reactions)
        )
        assertEquals(listOf("rainbell_after_rain"), discoveryIds(first.save))
        assertEquals(
            listOf("discovery.rainbell_after_rain"),
            first.save.appliedTerrariumReactionEventIds
        )
        assertEquals(1, first.newlyAppliedDurableEvents.size)

        val second = service.evaluateAndApply(first.save)
        assertEquals(
            listOf("butterfly" to "rainbell-001"),
            visitorPairs(second.reactions)
        )
        assertEquals(listOf("rainbell_after_rain"), discoveryIds(second.save))
        assertTrue(second.newlyAppliedDurableEvents.isEmpty())
    }

    @Test
    fun visitorRequiresBothRainAndRainbell() {
        val service = TerrariumReactionSaveService(catalog, visitorReactionCatalog())

        val clear = service.evaluateAndApply(
            SaveData(
                terrariumLayout = rainbellLayout(),
                terrariumEnvironment = EnvironmentState(weatherEcho = clearEcho())
            )
        )
        assertTrue(visitorPairs(clear.reactions).isEmpty())
        assertTrue(discoveryIds(clear.save).isEmpty())

        val noRainbell = service.evaluateAndApply(
            SaveData(
                terrariumEnvironment = EnvironmentState(weatherEcho = rainEcho("echo-no-rainbell"))
            )
        )
        assertTrue(visitorPairs(noRainbell.reactions).isEmpty())
        assertTrue(discoveryIds(noRainbell.save).isEmpty())
    }

    @Test
    fun multipleRainbellsChooseCanonicalVisitorSource() {
        val service = TerrariumReactionSaveService(catalog, visitorReactionCatalog())
        val save = SaveData(
            terrariumLayout = TerrariumLayout(
                placements = listOf(
                    placement("rainbell-z", 0.8f),
                    placement("rainbell-a", 0.2f)
                )
            ),
            terrariumEnvironment = EnvironmentState(weatherEcho = rainEcho("echo-canonical-visitor"))
        )

        val result = service.evaluateAndApply(save)

        assertEquals(listOf("butterfly" to "rainbell-a"), visitorPairs(result.reactions))
        assertEquals(listOf("rainbell_after_rain"), discoveryIds(result.save))
        assertEquals("rainbell-a", result.newlyAppliedDurableEvents.single().sourceInstanceId)
    }

    @Test
    fun visitorPresenceIsNeverSerializedIntoSaveState() {
        val service = TerrariumReactionSaveService(catalog, visitorReactionCatalog())
        val result = service.evaluateAndApply(rainyRainbellSave("echo-ephemeral-visitor"))
        val encodedSave = json.encodeToJsonElement(SaveData.serializer(), result.save).jsonObject

        assertFalse(encodedSave.containsKey("terrariumVisitors"))
        assertFalse(encodedSave.containsKey("visitors"))
        assertEquals(listOf("rainbell_after_rain"), discoveryIds(result.save))
        assertEquals(listOf("butterfly" to "rainbell-001"), visitorPairs(result.reactions))
    }

    @Test
    fun shippedRainbellReactionDeclaresButterflyVisitor() {
        val asset = shippedReactionAsset()
        val root = json.parseToJsonElement(asset.readText()).jsonObject
        val rainbellRule = root.getValue("rules").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("id").jsonPrimitive.content == "rainbell-after-rain" }
        val result = rainbellRule.getValue("result").jsonObject
        val visitors = result["visitorIds"]?.jsonArray

        assertNotNull("rainbell-after-rain must declare visitorIds", visitors)
        assertEquals(listOf("butterfly"), visitors!!.map { it.jsonPrimitive.content })
    }

    private fun visitorPairs(result: ReactionResult): List<Pair<String, String>> {
        val encoded = json.encodeToJsonElement(ReactionResult.serializer(), result).jsonObject
        val visitors = encoded["visitors"]?.jsonArray
        assertNotNull("ReactionResult must expose recomputable visitors", visitors)
        return visitors!!.map { element ->
            val visitor = element.jsonObject
            visitor.getValue("visitorId").jsonPrimitive.content to
                visitor.getValue("sourceInstanceId").jsonPrimitive.content
        }
    }

    private fun discoveryIds(save: SaveData): List<String> {
        val encoded = json.encodeToJsonElement(SaveData.serializer(), save).jsonObject
        val discoveries = encoded["terrariumDiscoveries"]?.jsonArray
        assertNotNull("SaveData must persist terrariumDiscoveries", discoveries)
        return discoveries!!.map { it.jsonPrimitive.content }
    }

    private fun visitorReactionCatalog(): ReactionCatalog = ReactionCatalog.decode(
        """
        {
          "schemaVersion": 1,
          "rules": [
            {
              "id": "rainbell-after-rain",
              "requires": {
                "itemTags": ["rainbell"],
                "weatherKinds": ["Rain"]
              },
              "result": {
                "visualTags": ["bloom", "wet"],
                "visitorIds": ["butterfly"],
                "durableEvents": [
                  {
                    "id": "discovery.rainbell_after_rain",
                    "kind": "DiscoveryCandidate",
                    "payloadId": "rainbell_after_rain"
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()
    )

    private fun rainyRainbellSave(echoId: String) = SaveData(
        terrariumLayout = rainbellLayout(),
        terrariumEnvironment = EnvironmentState(weatherEcho = rainEcho(echoId))
    )

    private fun rainbellLayout() = TerrariumLayout(placements = listOf(placement("rainbell-001", 0.5f)))

    private fun placement(instanceId: String, x: Float) = TerrariumPlacement(
        instanceId = instanceId,
        itemId = "rainbell",
        xNormalized = x,
        yNormalized = 0.6f,
        rotation = TerrariumRotation.Deg0,
        logicalFootprint = TerrariumFootprint(1, 1)
    )

    private fun rainbell() = TerrariumItem(
        id = "rainbell",
        nameKey = "terrarium.item.rainbell",
        category = TerrariumCategory.Botanical,
        visualFamily = "bell-flower",
        footprint = TerrariumFootprint(1, 1),
        allowedRotations = listOf(TerrariumRotation.Deg0),
        reactionTags = listOf("plant", "rainbell", "rain"),
        growthProfileId = "botanical"
    )

    private fun rainEcho(id: String) = WeatherEchoSnapshot(
        id = id,
        kinds = listOf(WeatherEchoKind.Rain),
        rainIntensity = 1,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Rain
    )

    private fun clearEcho() = WeatherEchoSnapshot(
        id = "echo-clear-visitor",
        kinds = listOf(WeatherEchoKind.Clear),
        rainIntensity = 0,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Clear
    )

    private fun shippedReactionAsset(): File {
        val candidates = listOf(
            File("src/main/assets/terrarium_reactions.json"),
            File("app/src/main/assets/terrarium_reactions.json")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("terrarium_reactions.json not found from ${File(".").absolutePath}")
    }
}
