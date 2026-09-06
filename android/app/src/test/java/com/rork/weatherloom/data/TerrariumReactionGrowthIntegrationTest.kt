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
import com.rork.weatherloom.core.terrarium.reaction.DurableReactionEventDefinition
import com.rork.weatherloom.core.terrarium.reaction.DurableReactionEventKind
import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.terrarium.reaction.ReactionCatalog
import com.rork.weatherloom.core.terrarium.reaction.ReactionRequirements
import com.rork.weatherloom.core.terrarium.reaction.ReactionRule
import com.rork.weatherloom.core.terrarium.reaction.ReactionRuleOutput
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrariumReactionGrowthIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val catalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = listOf(
            GrowthProfile(
                id = "botanical",
                stages = listOf(
                    GrowthStage.Seed,
                    GrowthStage.Sprout,
                    GrowthStage.Young,
                    GrowthStage.Mature,
                    GrowthStage.Bloom
                )
            )
        ),
        items = listOf(rainbell())
    )
    private val reactions = ReactionCatalog(
        schemaVersion = 1,
        rules = listOf(rainbellAfterRainRule())
    )
    private val service = TerrariumReactionSaveService(catalog, reactions)

    @Test
    fun growthAndDurableReactionAreAppliedToTheSameReturnedSave() {
        val result = service.evaluateAndApply(saveWithRain("echo-rain-1"))

        val growth = result.save.terrariumGrowth.single()
        assertEquals("rainbell-001", growth.instanceId)
        assertEquals(1, growth.stageIndex)
        assertEquals(1, growth.growthPulsesApplied)
        assertEquals("echo-rain-1", growth.lastRelevantEchoId)
        assertEquals(
            listOf("discovery.rainbell_after_rain"),
            result.save.appliedTerrariumReactionEventIds
        )
        assertEquals(
            listOf("discovery.rainbell_after_rain"),
            result.newlyAppliedDurableEvents.map { it.id }
        )
    }

    @Test
    fun sameEchoRemainsGrowthIdempotentAcrossSerializedRestart() {
        val first = service.evaluateAndApply(saveWithRain("echo-rain-restart"))
        val encoded = json.encodeToString(SaveData.serializer(), first.save)
        val restarted = SaveMigration.decode(encoded, json)

        val second = service.evaluateAndApply(restarted)

        assertEquals(first.save.terrariumGrowth, second.save.terrariumGrowth)
        assertEquals(1, second.save.terrariumGrowth.single().growthPulsesApplied)
        assertTrue(second.newlyAppliedDurableEvents.isEmpty())
        assertTrue(second.reactions.pendingDurableEvents.isEmpty())
        assertEquals(first.save, second.save)
    }

    private fun saveWithRain(echoId: String) = SaveData(
        terrariumLayout = TerrariumLayout(
            placements = listOf(
                TerrariumPlacement(
                    instanceId = "rainbell-001",
                    itemId = "rainbell",
                    xNormalized = 0.4f,
                    yNormalized = 0.6f,
                    rotation = TerrariumRotation.Deg0,
                    logicalFootprint = TerrariumFootprint(1, 1)
                )
            )
        ),
        terrariumEnvironment = EnvironmentState(rainEcho(echoId))
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

    private fun rainbellAfterRainRule() = ReactionRule(
        id = "rainbell-after-rain",
        requires = ReactionRequirements(
            itemTags = listOf("rainbell"),
            weatherKinds = listOf(WeatherEchoKind.Rain)
        ),
        result = ReactionRuleOutput(
            visualTags = listOf("bloom", "wet"),
            durableEvents = listOf(
                DurableReactionEventDefinition(
                    id = "discovery.rainbell_after_rain",
                    kind = DurableReactionEventKind.DiscoveryCandidate,
                    payloadId = "rainbell_after_rain"
                )
            )
        )
    )

    private fun rainEcho(id: String) = WeatherEchoSnapshot(
        id = id,
        kinds = listOf(WeatherEchoKind.Rain),
        rainIntensity = 2,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Rain
    )
}
