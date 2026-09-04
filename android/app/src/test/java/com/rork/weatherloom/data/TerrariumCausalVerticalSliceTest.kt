package com.rork.weatherloom.data

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

class TerrariumCausalVerticalSliceTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val catalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = emptyList(),
        items = listOf(rainbell())
    )
    private val reactions = ReactionCatalog(
        schemaVersion = 1,
        rules = listOf(rainbellAfterRainRule())
    )

    @Test
    fun solvedPuzzlePersistsItsWeatherEchoAsTerrariumEnvironment() {
        val service = PuzzleSolveService(PuzzleRewardBridge(catalog))
        val echo = rainEcho("echo-v1-solve-rain")

        val result = service.recordSolve(
            save = SaveData(),
            levelId = "c1-rain",
            rating = Rating.Seedling,
            strokes = 3,
            cells = 12,
            rewardId = "rainbell",
            weatherEcho = echo
        )

        assertEquals(EnvironmentState(weatherEcho = echo), result.save.terrariumEnvironment)
        assertTrue(result.save.terrariumInventory.owns("rainbell"))
        assertEquals(100, result.save.playerProgression.xp)
    }

    @Test
    fun solveWithoutNewEchoPreservesPreviouslyPersistedEnvironment() {
        val service = PuzzleSolveService(PuzzleRewardBridge(catalog))
        val existing = EnvironmentState(weatherEcho = rainEcho("echo-v1-existing"))
        val save = SaveData(terrariumEnvironment = existing)

        val result = service.recordSolve(
            save = save,
            levelId = "c1-no-echo",
            rating = Rating.Seedling,
            strokes = 4,
            cells = 15,
            rewardId = null,
            weatherEcho = null
        )

        assertEquals(existing, result.save.terrariumEnvironment)
    }

    @Test
    fun rainbellReactionAppliesDurableEventExactlyOnceAcrossRestart() {
        val bridge = TerrariumReactionSaveService(catalog, reactions)
        val original = SaveData(
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
            terrariumEnvironment = EnvironmentState(
                weatherEcho = rainEcho("echo-v1-rain-restart")
            )
        )

        val first = bridge.evaluateAndApply(original)

        assertEquals(listOf("bloom", "wet"), first.reactions.visualStates.single().visualTags)
        assertEquals(
            listOf("discovery.rainbell_after_rain"),
            first.newlyAppliedDurableEvents.map { it.id }
        )
        assertEquals(
            listOf("discovery.rainbell_after_rain"),
            first.save.appliedTerrariumReactionEventIds
        )

        val encoded = json.encodeToString(SaveData.serializer(), first.save)
        val restartedSave = SaveMigration.decode(encoded, json)
        val afterRestart = bridge.evaluateAndApply(restartedSave)

        assertEquals(listOf("bloom", "wet"), afterRestart.reactions.visualStates.single().visualTags)
        assertTrue(afterRestart.newlyAppliedDurableEvents.isEmpty())
        assertTrue(afterRestart.reactions.pendingDurableEvents.isEmpty())
        assertEquals(first.save, afterRestart.save)
    }

    @Test
    fun noPersistedEnvironmentProducesNoReactionAndNoSaveMutation() {
        val bridge = TerrariumReactionSaveService(catalog, reactions)
        val save = SaveData(
            terrariumLayout = TerrariumLayout(
                placements = listOf(
                    TerrariumPlacement(
                        instanceId = "rainbell-001",
                        itemId = "rainbell",
                        xNormalized = 0.4f,
                        yNormalized = 0.6f,
                        logicalFootprint = TerrariumFootprint(1, 1)
                    )
                )
            )
        )

        val result = bridge.evaluateAndApply(save)

        assertEquals(save, result.save)
        assertTrue(result.reactions.visualStates.isEmpty())
        assertTrue(result.reactions.pendingDurableEvents.isEmpty())
        assertTrue(result.newlyAppliedDurableEvents.isEmpty())
    }

    @Test
    fun schemaFourSaveMigratesToCurrentWithReactionDefaultsWithoutLosingState() {
        val raw = """
            {
              "schema": 4,
              "levels": {"c1-1": {"rating": 2, "attempts": 3}},
              "collectibles": ["rainbell"],
              "playerProgression": {
                "xp": 150,
                "awardedLevelXp": {"c1-1": 150}
              },
              "terrariumLayout": {
                "placements": [
                  {
                    "instanceId": "rainbell-001",
                    "itemId": "rainbell",
                    "xNormalized": 0.4,
                    "yNormalized": 0.6,
                    "rotation": "Deg0",
                    "logicalFootprint": {"width": 1, "height": 1},
                    "depthLayer": 0
                  }
                ]
              }
            }
        """.trimIndent()

        val migrated = SaveMigration.decode(raw, json)

        assertEquals(CURRENT_SAVE_SCHEMA, migrated.schema)
        assertEquals(2, migrated.levels.getValue("c1-1").rating)
        assertEquals(150, migrated.playerProgression.xp)
        assertEquals("rainbell-001", migrated.terrariumLayout.placements.single().instanceId)
        assertEquals(null, migrated.terrariumEnvironment)
        assertTrue(migrated.appliedTerrariumReactionEventIds.isEmpty())
    }

    private fun rainbell() = TerrariumItem(
        id = "rainbell",
        nameKey = "terrarium.item.rainbell",
        category = TerrariumCategory.Botanical,
        visualFamily = "bell-flower",
        footprint = TerrariumFootprint(1, 1),
        allowedRotations = listOf(TerrariumRotation.Deg0),
        reactionTags = listOf("plant", "rainbell", "rain"),
        assetRef = "spec_rainbell"
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
