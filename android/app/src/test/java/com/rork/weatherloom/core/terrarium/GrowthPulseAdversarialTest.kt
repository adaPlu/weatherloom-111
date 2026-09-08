package com.rork.weatherloom.core.terrarium

import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class GrowthPulseAdversarialTest {

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
                nameKey = "rainbell",
                category = TerrariumCategory.Botanical,
                visualFamily = "rainbell",
                footprint = TerrariumFootprint(1, 1),
                allowedRotations = listOf(TerrariumRotation.Deg0),
                reactionTags = listOf("plant", "rain"),
                growthProfileId = "botanical"
            )
        )
    )
    private val service = GrowthPulseService(catalog)
    private val layout = TerrariumLayout(
        listOf(
            TerrariumPlacement(
                instanceId = "rainbell-001",
                itemId = "rainbell",
                xNormalized = 0.5f,
                yNormalized = 0.5f,
                logicalFootprint = TerrariumFootprint(1, 1)
            )
        )
    )

    @Test
    fun oldEchoCannotBeReplayedAfterAnotherEchoHasGrownPlant() {
        val first = service.apply(layout, emptyList(), rain("echo-a"))
        val second = service.apply(layout, first, rain("echo-b"))
        val replayedOldEcho = service.apply(layout, second, rain("echo-a"))

        assertEquals(2, replayedOldEcho.single().growthPulsesApplied)
        assertEquals(second, replayedOldEcho)
    }

    @Test
    fun temporarilyStoredPlantKeepsGrowthStateUntilItIsPlacedAgain() {
        val grown = service.apply(layout, emptyList(), rain("echo-a"))
        val stored = service.apply(TerrariumLayout(), grown, rain("echo-b"))
        val returned = service.apply(layout, stored, rain("echo-c"))

        assertEquals(grown, stored)
        assertEquals(2, returned.single().growthPulsesApplied)
        assertEquals("rainbell-001", returned.single().instanceId)
    }

    @Test
    fun legacyOrRecoveredGrowthStateCanNeverRegressItsStage() {
        val legacyBloom = GrowthState(
            instanceId = "rainbell-001",
            growthProfileId = "botanical",
            stageIndex = profile.stages.lastIndex,
            growthPulsesApplied = 0
        )

        val result = service.apply(layout, listOf(legacyBloom), rain("echo-new"))

        assertEquals(profile.stages.lastIndex, result.single().stageIndex)
        assertEquals(GrowthStage.Bloom, profile.stages[result.single().stageIndex])
    }

    @Test
    fun terminalBloomIgnoresFutureRelevantEchoesWithoutGrowingHistory() {
        val terminal = GrowthState(
            instanceId = "rainbell-001",
            growthProfileId = "botanical",
            stageIndex = profile.stages.lastIndex,
            growthPulsesApplied = 2,
            lastRelevantEchoId = "echo-b",
            appliedEchoIds = listOf("echo-a", "echo-b")
        )

        val afterManyEchoes = (1..100).fold(listOf(terminal)) { state, index ->
            service.apply(layout, state, rain("future-echo-$index"))
        }

        assertEquals(listOf(terminal), afterManyEchoes)
    }

    private fun rain(id: String) = WeatherEchoSnapshot(
        id = id,
        kinds = listOf(WeatherEchoKind.Rain),
        rainIntensity = 1,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Rain
    )
}
