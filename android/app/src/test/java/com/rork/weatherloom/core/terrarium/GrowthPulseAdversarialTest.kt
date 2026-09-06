package com.rork.weatherloom.core.terrarium

import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GrowthPulseAdversarialTest {

    @Test
    fun storedGrowthStateSurvivesOffLayoutAndResumesWhenReplaced() {
        val catalog = catalog(item("rainbell", "rain", "botanical"), profile("botanical"))
        val stored = GrowthState(
            instanceId = "rainbell-1",
            growthProfileId = "botanical",
            stageIndex = 1,
            growthPulsesApplied = 1,
            lastRelevantEchoId = "echo-before-store"
        )

        val whileStored = GrowthPulseService.apply(
            layout = TerrariumLayout(),
            growthStates = listOf(stored),
            environment = environment("echo-while-stored", WeatherEchoKind.Rain),
            catalog = catalog
        )

        assertEquals(listOf(stored), whileStored.growthStates)
        assertTrue(whileStored.pulsedInstanceIds.isEmpty())

        val afterReplace = GrowthPulseService.apply(
            layout = layout(placement("rainbell-1", "rainbell")),
            growthStates = whileStored.growthStates,
            environment = environment("echo-after-replace", WeatherEchoKind.Rain),
            catalog = catalog
        )

        assertEquals(2, afterReplace.growthStates.single().stageIndex)
        assertEquals(2, afterReplace.growthStates.single().growthPulsesApplied)
        assertEquals("echo-after-replace", afterReplace.growthStates.single().lastRelevantEchoId)
    }

    @Test
    fun duplicateGrowthIdentityIsRejectedBeforeMutation() {
        val catalog = catalog(item("rainbell", "rain", "botanical"), profile("botanical"))
        val duplicated = GrowthState("same-id", "botanical")

        expectIllegalArgument {
            GrowthPulseService.apply(
                layout = layout(placement("same-id", "rainbell")),
                growthStates = listOf(duplicated, duplicated.copy()),
                environment = environment("echo-rain", WeatherEchoKind.Rain),
                catalog = catalog
            )
        }
    }

    @Test
    fun growthProfileIdentityCannotSilentlyChangeForAnExistingInstance() {
        val catalog = TerrariumCatalog(
            schemaVersion = 1,
            growthProfiles = listOf(profile("botanical"), profile("other")),
            items = listOf(item("rainbell", "rain", "botanical"))
        )
        val mismatched = GrowthState(
            instanceId = "rainbell-1",
            growthProfileId = "other"
        )

        expectIllegalArgument {
            GrowthPulseService.apply(
                layout = layout(placement("rainbell-1", "rainbell")),
                growthStates = listOf(mismatched),
                environment = environment("echo-rain", WeatherEchoKind.Rain),
                catalog = catalog
            )
        }
    }

    @Test
    fun singleStageProfileCannotOverflowAndConsumesRelevantEchoIdempotently() {
        val oneStage = GrowthProfile(
            id = "single-stage",
            stages = listOf(GrowthStage.Bloom),
            pulsesPerStage = 3
        )
        val catalog = catalog(item("moss", "moisture", "single-stage"), oneStage)
        val layout = layout(placement("moss-1", "moss"))
        val rain = environment("echo-rain", WeatherEchoKind.Rain)

        val first = GrowthPulseService.apply(layout, emptyList(), rain, catalog)
        val second = GrowthPulseService.apply(layout, first.growthStates, rain, catalog)

        assertEquals(0, first.growthStates.single().stageIndex)
        assertEquals(0, first.growthStates.single().growthPulsesApplied)
        assertEquals("echo-rain", first.growthStates.single().lastRelevantEchoId)
        assertTrue(first.pulsedInstanceIds.isEmpty())
        assertEquals(first.growthStates, second.growthStates)
        assertTrue(second.pulsedInstanceIds.isEmpty())
    }

    @Test
    fun irrelevantWeatherDoesNotRewriteExistingBiologicalState() {
        val catalog = catalog(item("rainbell", "rain", "botanical"), profile("botanical"))
        val existing = GrowthState(
            instanceId = "rainbell-1",
            growthProfileId = "botanical",
            stageIndex = 2,
            growthPulsesApplied = 2,
            lastRelevantEchoId = "echo-rain-old"
        )

        val result = GrowthPulseService.apply(
            layout = layout(placement("rainbell-1", "rainbell")),
            growthStates = listOf(existing),
            environment = environment("echo-wind", WeatherEchoKind.Wind),
            catalog = catalog
        )

        assertEquals(listOf(existing), result.growthStates)
        assertTrue(result.pulsedInstanceIds.isEmpty())
    }

    private fun profile(id: String) = GrowthProfile(
        id = id,
        stages = listOf(
            GrowthStage.Seed,
            GrowthStage.Sprout,
            GrowthStage.Young,
            GrowthStage.Mature,
            GrowthStage.Bloom
        )
    )

    private fun item(id: String, relevantTag: String, profileId: String) = TerrariumItem(
        id = id,
        nameKey = "terrarium.item.$id",
        category = TerrariumCategory.Botanical,
        visualFamily = id,
        footprint = TerrariumFootprint(1, 1),
        allowedRotations = listOf(TerrariumRotation.Deg0),
        reactionTags = listOf("plant", relevantTag),
        growthProfileId = profileId
    )

    private fun catalog(item: TerrariumItem, profile: GrowthProfile) = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = listOf(profile),
        items = listOf(item)
    )

    private fun placement(instanceId: String, itemId: String) = TerrariumPlacement(
        instanceId = instanceId,
        itemId = itemId,
        xNormalized = 0.5f,
        yNormalized = 0.5f,
        rotation = TerrariumRotation.Deg0,
        logicalFootprint = TerrariumFootprint(1, 1)
    )

    private fun layout(vararg placements: TerrariumPlacement) = TerrariumLayout(
        placements = placements.toList()
    )

    private fun environment(id: String, kind: WeatherEchoKind) = EnvironmentState(
        weatherEcho = WeatherEchoSnapshot(
            id = id,
            kinds = listOf(kind),
            rainIntensity = if (kind == WeatherEchoKind.Rain) 1 else 0,
            snowIntensity = if (kind == WeatherEchoKind.Snow) 1 else 0,
            windIntensity = if (kind == WeatherEchoKind.Wind) 1 else 0,
            primaryKind = kind
        )
    )

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected invariant rejection.
        }
    }
}
