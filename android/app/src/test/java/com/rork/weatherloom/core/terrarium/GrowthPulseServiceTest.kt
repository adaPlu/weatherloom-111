package com.rork.weatherloom.core.terrarium

import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthPulseServiceTest {

    private val botanical = GrowthProfile(
        id = "botanical",
        stages = listOf(
            GrowthStage.Seed,
            GrowthStage.Sprout,
            GrowthStage.Young,
            GrowthStage.Mature,
            GrowthStage.Bloom
        ),
        pulsesPerStage = 2
    )

    private val catalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = listOf(botanical),
        items = listOf(
            TerrariumItem(
                id = "rainbell",
                nameKey = "rainbell",
                category = TerrariumCategory.Botanical,
                visualFamily = "rainbell",
                footprint = TerrariumFootprint(1, 1),
                allowedRotations = listOf(TerrariumRotation.Deg0),
                reactionTags = listOf("plant", "rainbell", "rain"),
                growthProfileId = "botanical"
            ),
            TerrariumItem(
                id = "frostfern",
                nameKey = "frostfern",
                category = TerrariumCategory.Botanical,
                visualFamily = "frostfern",
                footprint = TerrariumFootprint(1, 1),
                allowedRotations = listOf(TerrariumRotation.Deg0),
                reactionTags = listOf("plant", "frostfern", "cold"),
                growthProfileId = "botanical"
            ),
            TerrariumItem(
                id = "windreed",
                nameKey = "windreed",
                category = TerrariumCategory.Botanical,
                visualFamily = "windreed",
                footprint = TerrariumFootprint(1, 1),
                allowedRotations = listOf(TerrariumRotation.Deg0),
                reactionTags = listOf("plant", "windreed", "wind"),
                growthProfileId = "botanical"
            ),
            TerrariumItem(
                id = "sunlace",
                nameKey = "sunlace",
                category = TerrariumCategory.Botanical,
                visualFamily = "sunlace",
                footprint = TerrariumFootprint(1, 1),
                allowedRotations = listOf(TerrariumRotation.Deg0),
                reactionTags = listOf("plant", "sunlace", "warm"),
                growthProfileId = "botanical"
            ),
            TerrariumItem(
                id = "stone",
                nameKey = "stone",
                category = TerrariumCategory.Decoration,
                visualFamily = "stone",
                footprint = TerrariumFootprint(1, 1),
                allowedRotations = listOf(TerrariumRotation.Deg0),
                reactionTags = listOf("stone")
            )
        )
    )

    private val service = GrowthPulseService(catalog)

    @Test
    fun relevantRainGrowsRainbellAndRainSuppliesMoistureSemantic() {
        val layout = layoutOf("rainbell-1" to "rainbell")
        val rain = echo("rain-1", rain = 2)

        val result = service.apply(layout, emptyList(), rain)

        assertEquals(1, result.size)
        assertEquals("rainbell-1", result.single().instanceId)
        assertEquals(1, result.single().growthPulsesApplied)
        assertEquals("rain-1", result.single().lastRelevantEchoId)
        assertTrue(service.semantics(rain).containsAll(setOf("rain", "moisture")))
    }

    @Test
    fun snowSuppliesColdClearSuppliesWarmAndWindSuppliesWind() {
        assertTrue(service.semantics(echo("snow", snow = 2)).containsAll(setOf("snow", "cold")))
        assertTrue(service.semantics(clearEcho("clear")).containsAll(setOf("clear", "warm")))
        assertTrue(service.semantics(echo("wind", wind = 2)).contains("wind"))
    }

    @Test
    fun mixedWeatherUsesUnionOfSemantics() {
        val mixed = echo("mixed", rain = 1, snow = 2, wind = 1)
        assertEquals(
            setOf("rain", "moisture", "snow", "cold", "wind"),
            service.semantics(mixed)
        )
    }

    @Test
    fun sameWeatherEchoCannotGrowSamePlantTwice() {
        val layout = layoutOf("rainbell-1" to "rainbell")
        val rain = echo("same", rain = 2)
        val once = service.apply(layout, emptyList(), rain)
        val twice = service.apply(layout, once, rain)

        assertEquals(once, twice)
    }

    @Test
    fun irrelevantWeatherDoesNotCreateGrowthState() {
        val layout = layoutOf("rainbell-1" to "rainbell")
        val snow = echo("snow", snow = 2)

        assertTrue(service.apply(layout, emptyList(), snow).isEmpty())
    }

    @Test
    fun pulsesPerStageControlsStageProgressionAndGrowthStateStopsAtBloom() {
        val layout = layoutOf("rainbell-1" to "rainbell")
        var states = emptyList<GrowthState>()

        repeat(10) { index ->
            states = service.apply(layout, states, echo("rain-$index", rain = 1))
        }

        val state = states.single()
        val pulsesToBloom = botanical.stages.lastIndex * botanical.pulsesPerStage
        assertEquals(GrowthStage.Bloom, botanical.stages[state.stageIndex])
        assertEquals(botanical.stages.lastIndex, state.stageIndex)
        assertEquals(pulsesToBloom, state.growthPulsesApplied)
        assertEquals(pulsesToBloom, state.appliedEchoIds.size)
        assertEquals("rain-${pulsesToBloom - 1}", state.lastRelevantEchoId)
    }

    @Test
    fun movingObjectDoesNotResetGrowthBecauseInstanceIdIsStable() {
        val original = layoutOf("rainbell-1" to "rainbell")
        val grown = service.apply(original, emptyList(), echo("rain-1", rain = 1))
        val moved = TerrariumLayout(
            listOf(original.placements.single().copy(xNormalized = 0.8f, yNormalized = 0.2f))
        )

        val result = service.apply(moved, grown, echo("rain-2", rain = 1))

        assertEquals(2, result.single().growthPulsesApplied)
        assertEquals(GrowthStage.Sprout, botanical.stages[result.single().stageIndex])
    }

    @Test
    fun nonGrowingDecorationsAreIgnored() {
        val layout = layoutOf("stone-1" to "stone")
        assertTrue(service.apply(layout, emptyList(), clearEcho("clear")).isEmpty())
    }

    @Test
    fun multiObjectResultsHaveDeterministicInstanceOrdering() {
        val layout = layoutOf(
            "z-rainbell" to "rainbell",
            "a-windreed" to "windreed"
        )
        val mixed = echo("rain-wind", rain = 1, wind = 1)

        val result = service.apply(layout, emptyList(), mixed)

        assertEquals(listOf("a-windreed", "z-rainbell"), result.map { it.instanceId })
    }

    private fun layoutOf(vararg entries: Pair<String, String>): TerrariumLayout =
        TerrariumLayout(
            entries.mapIndexed { index, (instanceId, itemId) ->
                TerrariumPlacement(
                    instanceId = instanceId,
                    itemId = itemId,
                    xNormalized = 0.1f + index * 0.2f,
                    yNormalized = 0.5f,
                    logicalFootprint = TerrariumFootprint(1, 1)
                )
            }
        )

    private fun echo(id: String, rain: Int = 0, snow: Int = 0, wind: Int = 0): WeatherEchoSnapshot {
        val kinds = buildList {
            if (rain > 0) add(WeatherEchoKind.Rain)
            if (snow > 0) add(WeatherEchoKind.Snow)
            if (wind > 0) add(WeatherEchoKind.Wind)
        }
        val primary = listOf(
            WeatherEchoKind.Rain to rain,
            WeatherEchoKind.Snow to snow,
            WeatherEchoKind.Wind to wind
        ).filter { it.second > 0 }.maxByOrNull { it.second }?.first
        return WeatherEchoSnapshot(id, kinds, rain, snow, wind, primary)
    }

    private fun clearEcho(id: String) = WeatherEchoSnapshot(
        id = id,
        kinds = listOf(WeatherEchoKind.Clear),
        rainIntensity = 0,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Clear
    )
}
