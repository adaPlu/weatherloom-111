package com.rork.weatherloom.core.terrarium

import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthPulseServiceTest {

    @Test
    fun relevantRainEchoCreatesStateAndAdvancesOneStage() {
        val catalog = catalog(
            item("rainbell", tags = listOf("plant", "rain"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )
        val result = GrowthPulseService.apply(
            layout = layout(placement("rainbell-1", "rainbell")),
            growthStates = emptyList(),
            environment = EnvironmentState(rain("echo-rain-1")),
            catalog = catalog
        )

        val growth = result.growthStates.single()
        assertEquals("rainbell-1", growth.instanceId)
        assertEquals("botanical", growth.growthProfileId)
        assertEquals(1, growth.stageIndex)
        assertEquals(1, growth.growthPulsesApplied)
        assertEquals("echo-rain-1", growth.lastRelevantEchoId)
        assertEquals(listOf("rainbell-1"), result.pulsedInstanceIds)
    }

    @Test
    fun sameEchoIdCannotGrowTheSamePlantTwice() {
        val catalog = catalog(
            item("rainbell", tags = listOf("rain"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )
        val first = GrowthPulseService.apply(
            layout = layout(placement("rainbell-1", "rainbell")),
            growthStates = emptyList(),
            environment = EnvironmentState(rain("same-echo")),
            catalog = catalog
        )
        val repeated = GrowthPulseService.apply(
            layout = layout(placement("rainbell-1", "rainbell")),
            growthStates = first.growthStates,
            environment = EnvironmentState(rain("same-echo")),
            catalog = catalog
        )

        assertEquals(first.growthStates, repeated.growthStates)
        assertTrue(repeated.pulsedInstanceIds.isEmpty())
    }

    @Test
    fun irrelevantEchoDoesNotCreateGrowthState() {
        val catalog = catalog(
            item("rainbell", tags = listOf("rain"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )

        val result = GrowthPulseService.apply(
            layout = layout(placement("rainbell-1", "rainbell")),
            growthStates = emptyList(),
            environment = EnvironmentState(wind("wind-only")),
            catalog = catalog
        )

        assertTrue(result.growthStates.isEmpty())
        assertTrue(result.pulsedInstanceIds.isEmpty())
    }

    @Test
    fun rainAlsoMatchesMoistureTaggedPlants() {
        val catalog = catalog(
            item("cloudmoss", tags = listOf("moisture"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )

        val result = GrowthPulseService.apply(
            layout = layout(placement("moss-1", "cloudmoss")),
            growthStates = emptyList(),
            environment = EnvironmentState(rain("wet-echo")),
            catalog = catalog
        )

        assertEquals(1, result.growthStates.single().growthPulsesApplied)
    }

    @Test
    fun snowAlsoMatchesColdTaggedPlants() {
        val catalog = catalog(
            item("frostfern", tags = listOf("cold"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )

        val result = GrowthPulseService.apply(
            layout = layout(placement("fern-1", "frostfern")),
            growthStates = emptyList(),
            environment = EnvironmentState(snow("cold-echo")),
            catalog = catalog
        )

        assertEquals(1, result.growthStates.single().growthPulsesApplied)
    }

    @Test
    fun clearAlsoMatchesWarmTaggedPlants() {
        val catalog = catalog(
            item("sunlace", tags = listOf("warm"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )

        val result = GrowthPulseService.apply(
            layout = layout(placement("sun-1", "sunlace")),
            growthStates = emptyList(),
            environment = EnvironmentState(clear("clear-echo")),
            catalog = catalog
        )

        assertEquals(1, result.growthStates.single().growthPulsesApplied)
    }

    @Test
    fun mixedEchoKindsUseUnionOfSemanticTags() {
        val catalog = catalog(
            item("windreed", tags = listOf("wind"), profile = "botanical"),
            item("rainbell", tags = listOf("rain"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )
        val mixed = WeatherEchoSnapshot(
            id = "rain-wind",
            kinds = listOf(WeatherEchoKind.Rain, WeatherEchoKind.Wind),
            rainIntensity = 1,
            snowIntensity = 0,
            windIntensity = 2,
            primaryKind = WeatherEchoKind.Wind
        )

        val result = GrowthPulseService.apply(
            layout = layout(
                placement("wind-1", "windreed"),
                placement("rain-1", "rainbell")
            ),
            growthStates = emptyList(),
            environment = EnvironmentState(mixed),
            catalog = catalog
        )

        assertEquals(setOf("rain-1", "wind-1"), result.pulsedInstanceIds.toSet())
        assertEquals(2, result.growthStates.size)
    }

    @Test
    fun pulsesPerStageControlsWhenStageAdvances() {
        val slow = GrowthProfile(
            id = "slow",
            stages = listOf(GrowthStage.Seed, GrowthStage.Sprout, GrowthStage.Mature),
            pulsesPerStage = 2
        )
        val catalog = catalog(
            item("rainbell", tags = listOf("rain"), profile = "slow"),
            profiles = listOf(slow)
        )
        val placed = layout(placement("rainbell-1", "rainbell"))

        val first = GrowthPulseService.apply(
            layout = placed,
            growthStates = emptyList(),
            environment = EnvironmentState(rain("rain-a")),
            catalog = catalog
        )
        val second = GrowthPulseService.apply(
            layout = placed,
            growthStates = first.growthStates,
            environment = EnvironmentState(rain("rain-b")),
            catalog = catalog
        )

        assertEquals(0, first.growthStates.single().stageIndex)
        assertEquals(1, first.growthStates.single().growthPulsesApplied)
        assertEquals(1, second.growthStates.single().stageIndex)
        assertEquals(2, second.growthStates.single().growthPulsesApplied)
    }

    @Test
    fun maxStageCapsPulsesButConsumesNewRelevantEchoId() {
        val catalog = catalog(
            item("rainbell", tags = listOf("rain"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )
        val existing = GrowthState(
            instanceId = "rainbell-1",
            growthProfileId = "botanical",
            stageIndex = 4,
            growthPulsesApplied = 4,
            lastRelevantEchoId = "old-echo"
        )

        val result = GrowthPulseService.apply(
            layout = layout(placement("rainbell-1", "rainbell")),
            growthStates = listOf(existing),
            environment = EnvironmentState(rain("new-echo")),
            catalog = catalog
        )

        val growth = result.growthStates.single()
        assertEquals(4, growth.stageIndex)
        assertEquals(4, growth.growthPulsesApplied)
        assertEquals("new-echo", growth.lastRelevantEchoId)
        assertTrue(result.pulsedInstanceIds.isEmpty())
    }

    @Test
    fun movingPlacementDoesNotResetGrowthBecauseIdentityIsInstanceId() {
        val catalog = catalog(
            item("rainbell", tags = listOf("rain"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )
        val existing = GrowthState(
            instanceId = "rainbell-1",
            growthProfileId = "botanical",
            stageIndex = 2,
            growthPulsesApplied = 2,
            lastRelevantEchoId = "old"
        )
        val moved = TerrariumPlacement(
            instanceId = "rainbell-1",
            itemId = "rainbell",
            xNormalized = 0.83f,
            yNormalized = 0.19f,
            logicalFootprint = TerrariumFootprint(1, 1),
            depthLayer = 4
        )

        val result = GrowthPulseService.apply(
            layout = TerrariumLayout(listOf(moved)),
            growthStates = listOf(existing),
            environment = EnvironmentState(rain("new")),
            catalog = catalog
        )

        assertEquals(3, result.growthStates.single().stageIndex)
        assertEquals(3, result.growthStates.single().growthPulsesApplied)
    }

    @Test
    fun itemWithoutGrowthProfileIsSkipped() {
        val catalog = catalog(
            item("stone", tags = listOf("rain"), profile = null),
            profiles = emptyList()
        )

        val result = GrowthPulseService.apply(
            layout = layout(placement("stone-1", "stone")),
            growthStates = emptyList(),
            environment = EnvironmentState(rain("rain")),
            catalog = catalog
        )

        assertTrue(result.growthStates.isEmpty())
    }

    @Test
    fun outputOrderingIsDeterministicByInstanceId() {
        val catalog = catalog(
            item("rainbell", tags = listOf("rain"), profile = "botanical"),
            profiles = listOf(profile("botanical"))
        )
        val result = GrowthPulseService.apply(
            layout = layout(
                placement("z-instance", "rainbell"),
                placement("a-instance", "rainbell")
            ),
            growthStates = emptyList(),
            environment = EnvironmentState(rain("rain")),
            catalog = catalog
        )

        assertEquals(listOf("a-instance", "z-instance"), result.growthStates.map { it.instanceId })
        assertEquals(listOf("a-instance", "z-instance"), result.pulsedInstanceIds)
    }

    private fun profile(id: String): GrowthProfile = GrowthProfile(
        id = id,
        stages = listOf(
            GrowthStage.Seed,
            GrowthStage.Sprout,
            GrowthStage.Young,
            GrowthStage.Mature,
            GrowthStage.Bloom
        ),
        pulsesPerStage = 1
    )

    private fun item(
        id: String,
        tags: List<String>,
        profile: String?
    ): TerrariumItem = TerrariumItem(
        id = id,
        nameKey = "terrarium.item.$id",
        category = if (profile == null) TerrariumCategory.Decoration else TerrariumCategory.Botanical,
        visualFamily = id,
        footprint = TerrariumFootprint(1, 1),
        allowedRotations = listOf(TerrariumRotation.Deg0),
        reactionTags = tags,
        growthProfileId = profile
    )

    private fun catalog(
        vararg items: TerrariumItem,
        profiles: List<GrowthProfile>
    ): TerrariumCatalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = profiles,
        items = items.toList()
    )

    private fun placement(instanceId: String, itemId: String): TerrariumPlacement = TerrariumPlacement(
        instanceId = instanceId,
        itemId = itemId,
        xNormalized = 0.5f,
        yNormalized = 0.5f,
        logicalFootprint = TerrariumFootprint(1, 1)
    )

    private fun layout(vararg placements: TerrariumPlacement): TerrariumLayout =
        TerrariumLayout(placements.toList())

    private fun rain(id: String) = WeatherEchoSnapshot(
        id = id,
        kinds = listOf(WeatherEchoKind.Rain),
        rainIntensity = 1,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Rain
    )

    private fun snow(id: String) = WeatherEchoSnapshot(
        id = id,
        kinds = listOf(WeatherEchoKind.Snow),
        rainIntensity = 0,
        snowIntensity = 1,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Snow
    )

    private fun wind(id: String) = WeatherEchoSnapshot(
        id = id,
        kinds = listOf(WeatherEchoKind.Wind),
        rainIntensity = 0,
        snowIntensity = 0,
        windIntensity = 1,
        primaryKind = WeatherEchoKind.Wind
    )

    private fun clear(id: String) = WeatherEchoSnapshot(
        id = id,
        kinds = listOf(WeatherEchoKind.Clear),
        rainIntensity = 0,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Clear
    )
}
