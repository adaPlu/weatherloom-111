package com.rork.weatherloom.core.terrarium.reaction

import com.rork.weatherloom.core.terrarium.GrowthProfile
import com.rork.weatherloom.core.terrarium.GrowthStage
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumCategory
import com.rork.weatherloom.core.terrarium.TerrariumFootprint
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumLayout
import com.rork.weatherloom.core.terrarium.TerrariumPlacement
import com.rork.weatherloom.core.terrarium.TerrariumRotation
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReactionEngineTest {

    @Test
    fun rainbellReactsToRainWithEphemeralBloomWetAndDurableDiscovery() {
        val result = ReactionEngine.evaluate(
            layout = layout(placement("plant-001", "rainbell")),
            growthStates = emptyList(),
            environment = EnvironmentState(weatherEcho = rainEcho()),
            catalog = terrariumCatalog(rainbell()),
            reactions = reactionCatalog(rainbellAfterRainRule()),
            appliedDurableEventIds = emptySet()
        )

        assertEquals(
            listOf(ReactionVisualState("plant-001", listOf("bloom", "wet"))),
            result.visualStates
        )
        assertEquals(1, result.pendingDurableEvents.size)
        val event = result.pendingDurableEvents.single()
        assertEquals("discovery.rainbell_after_rain", event.id)
        assertEquals(DurableReactionEventKind.DiscoveryCandidate, event.kind)
        assertEquals("rainbell_after_rain", event.payloadId)
        assertEquals("rainbell-after-rain", event.ruleId)
        assertEquals("plant-001", event.sourceInstanceId)
    }

    @Test
    fun appliedDurableEventIsNotEmittedAgainButVisualStateRemains() {
        val result = ReactionEngine.evaluate(
            layout = layout(placement("plant-001", "rainbell")),
            growthStates = emptyList(),
            environment = EnvironmentState(weatherEcho = rainEcho()),
            catalog = terrariumCatalog(rainbell()),
            reactions = reactionCatalog(rainbellAfterRainRule()),
            appliedDurableEventIds = setOf("discovery.rainbell_after_rain")
        )

        assertEquals(
            listOf(ReactionVisualState("plant-001", listOf("bloom", "wet"))),
            result.visualStates
        )
        assertTrue(result.pendingDurableEvents.isEmpty())
    }

    @Test
    fun nonRainWeatherDoesNotTriggerRainbell() {
        val result = ReactionEngine.evaluate(
            layout = layout(placement("plant-001", "rainbell")),
            growthStates = emptyList(),
            environment = EnvironmentState(weatherEcho = clearEcho()),
            catalog = terrariumCatalog(rainbell()),
            reactions = reactionCatalog(rainbellAfterRainRule()),
            appliedDurableEventIds = emptySet()
        )

        assertTrue(result.visualStates.isEmpty())
        assertTrue(result.pendingDurableEvents.isEmpty())
    }

    @Test
    fun mixedRainAndWindStillSatisfiesRainRequirement() {
        val mixed = WeatherEchoSnapshot(
            id = "echo-v1-rain-wind",
            kinds = listOf(WeatherEchoKind.Rain, WeatherEchoKind.Wind),
            rainIntensity = 2,
            snowIntensity = 0,
            windIntensity = 1,
            primaryKind = WeatherEchoKind.Rain
        )

        val result = ReactionEngine.evaluate(
            layout = layout(placement("plant-001", "rainbell")),
            growthStates = emptyList(),
            environment = EnvironmentState(weatherEcho = mixed),
            catalog = terrariumCatalog(rainbell()),
            reactions = reactionCatalog(rainbellAfterRainRule()),
            appliedDurableEventIds = emptySet()
        )

        assertEquals(listOf("bloom", "wet"), result.visualStates.single().visualTags)
    }

    @Test
    fun ruleOrderingDoesNotChangeResult() {
        val wetRule = ReactionRule(
            id = "rainbell-wet-detail",
            requires = ReactionRequirements(
                itemTags = listOf("rainbell"),
                weatherKinds = listOf(WeatherEchoKind.Rain)
            ),
            result = ReactionRuleOutput(visualTags = listOf("glistening"))
        )
        val firstRules = reactionCatalog(rainbellAfterRainRule(), wetRule)
        val reversedRules = reactionCatalog(wetRule, rainbellAfterRainRule())
        val inputLayout = layout(placement("plant-001", "rainbell"))
        val environment = EnvironmentState(weatherEcho = rainEcho())
        val catalog = terrariumCatalog(rainbell())

        val first = ReactionEngine.evaluate(
            inputLayout,
            emptyList(),
            environment,
            catalog,
            firstRules,
            emptySet()
        )
        val reversed = ReactionEngine.evaluate(
            inputLayout,
            emptyList(),
            environment,
            catalog,
            reversedRules,
            emptySet()
        )

        assertEquals(first, reversed)
        assertEquals(listOf("bloom", "glistening", "wet"), first.visualStates.single().visualTags)
    }

    @Test
    fun missingCatalogItemIsIgnoredInsteadOfCrashing() {
        val result = ReactionEngine.evaluate(
            layout = layout(placement("legacy-001", "deleted-item")),
            growthStates = emptyList(),
            environment = EnvironmentState(weatherEcho = rainEcho()),
            catalog = terrariumCatalog(rainbell()),
            reactions = reactionCatalog(rainbellAfterRainRule()),
            appliedDurableEventIds = emptySet()
        )

        assertTrue(result.visualStates.isEmpty())
        assertTrue(result.pendingDurableEvents.isEmpty())
    }

    @Test
    fun multipleRainbellsEmitOneGlobalDiscoveryFromCanonicalSource() {
        val result = ReactionEngine.evaluate(
            layout = layout(
                placement("plant-z", "rainbell", x = 0.8f),
                placement("plant-a", "rainbell", x = 0.2f)
            ),
            growthStates = emptyList(),
            environment = EnvironmentState(weatherEcho = rainEcho()),
            catalog = terrariumCatalog(rainbell()),
            reactions = reactionCatalog(rainbellAfterRainRule()),
            appliedDurableEventIds = emptySet()
        )

        assertEquals(listOf("plant-a", "plant-z"), result.visualStates.map { it.instanceId })
        assertEquals(1, result.pendingDurableEvents.size)
        assertEquals("plant-a", result.pendingDurableEvents.single().sourceInstanceId)
    }

    @Test
    fun environmentModifiersMustBeStableUniqueAndCanonical() {
        assertIllegalArgument {
            EnvironmentState(weatherEcho = rainEcho(), modifiers = listOf("winter", "winter"))
        }
        assertIllegalArgument {
            EnvironmentState(weatherEcho = rainEcho(), modifiers = listOf("winter", "autumn"))
        }
        assertIllegalArgument {
            EnvironmentState(weatherEcho = rainEcho(), modifiers = listOf("Winter"))
        }

        val state = EnvironmentState(
            weatherEcho = rainEcho(),
            modifiers = listOf("autumn", "winter")
        )
        assertEquals(listOf("autumn", "winter"), state.modifiers)
    }

    @Test
    fun shippedReactionCatalogContainsRainbellAfterRainVerticalSlice() {
        val candidates = listOf(
            File("src/main/assets/terrarium_reactions.json"),
            File("app/src/main/assets/terrarium_reactions.json")
        )
        val asset = candidates.firstOrNull { it.isFile }
            ?: error("terrarium_reactions.json not found from ${File(".").absolutePath}")
        val decoded = ReactionCatalog.decode(asset.readText())

        assertEquals(1, decoded.schemaVersion)
        val rule = decoded.rules.single { it.id == "rainbell-after-rain" }
        assertEquals(listOf("rainbell"), rule.requires.itemTags)
        assertEquals(listOf(WeatherEchoKind.Rain), rule.requires.weatherKinds)
        assertEquals(listOf("bloom", "wet"), rule.result.visualTags)
        assertEquals("discovery.rainbell_after_rain", rule.result.durableEvents.single().id)
    }

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

    private fun reactionCatalog(vararg rules: ReactionRule) = ReactionCatalog(
        schemaVersion = 1,
        rules = rules.toList()
    )

    private fun rainbell() = TerrariumItem(
        id = "rainbell",
        nameKey = "terrarium.item.rainbell",
        category = TerrariumCategory.Botanical,
        visualFamily = "bell-flower",
        footprint = TerrariumFootprint(1, 1),
        allowedRotations = listOf(TerrariumRotation.Deg0),
        reactionTags = listOf("plant", "rainbell", "rain"),
        growthProfileId = "botanical-five-stage",
        assetRef = "spec_rainbell"
    )

    private fun terrariumCatalog(vararg items: TerrariumItem) = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = listOf(
            GrowthProfile(
                id = "botanical-five-stage",
                stages = listOf(
                    GrowthStage.Seed,
                    GrowthStage.Sprout,
                    GrowthStage.Young,
                    GrowthStage.Mature,
                    GrowthStage.Bloom
                )
            )
        ),
        items = items.toList()
    )

    private fun layout(vararg placements: TerrariumPlacement) =
        TerrariumLayout(placements.toList())

    private fun placement(
        instanceId: String,
        itemId: String,
        x: Float = 0.5f
    ) = TerrariumPlacement(
        instanceId = instanceId,
        itemId = itemId,
        xNormalized = x,
        yNormalized = 0.5f,
        logicalFootprint = TerrariumFootprint(1, 1)
    )

    private fun rainEcho() = WeatherEchoSnapshot(
        id = "echo-v1-rain",
        kinds = listOf(WeatherEchoKind.Rain),
        rainIntensity = 1,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Rain
    )

    private fun clearEcho() = WeatherEchoSnapshot(
        id = "echo-v1-clear",
        kinds = listOf(WeatherEchoKind.Clear),
        rainIntensity = 0,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Clear
    )

    private fun assertIllegalArgument(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
