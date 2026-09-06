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
import com.rork.weatherloom.core.terrarium.reaction.ReactionRequirements
import com.rork.weatherloom.core.terrarium.reaction.ReactionRule
import com.rork.weatherloom.core.terrarium.reaction.ReactionRuleOutput
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrariumGrowthReactionSaveServiceTest {

    private val profile = GrowthProfile(
        id = "botanical",
        stages = listOf(GrowthStage.Seed, GrowthStage.Sprout, GrowthStage.Bloom),
        pulsesPerStage = 1
    )
    private val catalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = listOf(profile),
        items = listOf(
            TerrariumItem(
                id = "rainbell",
                nameKey = "terrarium.item.rainbell",
                category = TerrariumCategory.Botanical,
                visualFamily = "bell-flower",
                footprint = TerrariumFootprint(1, 1),
                allowedRotations = listOf(TerrariumRotation.Deg0),
                reactionTags = listOf("plant", "rainbell", "rain"),
                growthProfileId = "botanical"
            )
        )
    )
    private val reactions = ReactionCatalog(
        schemaVersion = 1,
        rules = listOf(
            ReactionRule(
                id = "rainbell-after-rain",
                requires = ReactionRequirements(
                    itemTags = listOf("rainbell"),
                    weatherKinds = listOf(WeatherEchoKind.Rain)
                ),
                result = ReactionRuleOutput(visualTags = listOf("wet"))
            )
        )
    )
    private val service = TerrariumReactionSaveService(catalog, reactions)

    @Test
    fun oneEvaluationPersistsGrowthAndEvaluatesReactionFromSameSaveBoundary() {
        val save = rainyRainbellSave("echo-growth-1")

        val result = service.evaluateAndApply(save)

        assertEquals(1, result.save.terrariumGrowth.size)
        val growth = result.save.terrariumGrowth.single()
        assertEquals("rainbell-001", growth.instanceId)
        assertEquals(1, growth.growthPulsesApplied)
        assertEquals(GrowthStage.Sprout, profile.stages[growth.stageIndex])
        assertEquals("echo-growth-1", growth.lastRelevantEchoId)
        assertEquals(listOf("wet"), result.reactions.visualStates.single().visualTags)
    }

    @Test
    fun evaluatingSamePersistedEchoAgainDoesNotAddAnotherGrowthPulse() {
        val first = service.evaluateAndApply(rainyRainbellSave("echo-same"))
        val second = service.evaluateAndApply(first.save)

        assertEquals(1, second.save.terrariumGrowth.single().growthPulsesApplied)
        assertEquals(first.save.terrariumGrowth, second.save.terrariumGrowth)
    }

    @Test
    fun noEnvironmentLeavesGrowthUntouched() {
        val save = SaveData(
            terrariumLayout = rainbellLayout()
        )

        val result = service.evaluateAndApply(save)

        assertTrue(result.save.terrariumGrowth.isEmpty())
        assertEquals(save, result.save)
    }

    private fun rainyRainbellSave(echoId: String) = SaveData(
        terrariumLayout = rainbellLayout(),
        terrariumEnvironment = EnvironmentState(
            weatherEcho = WeatherEchoSnapshot(
                id = echoId,
                kinds = listOf(WeatherEchoKind.Rain),
                rainIntensity = 1,
                snowIntensity = 0,
                windIntensity = 0,
                primaryKind = WeatherEchoKind.Rain
            )
        )
    )

    private fun rainbellLayout() = TerrariumLayout(
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
    )
}
