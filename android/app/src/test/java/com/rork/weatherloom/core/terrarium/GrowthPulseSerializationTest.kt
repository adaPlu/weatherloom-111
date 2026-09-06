package com.rork.weatherloom.core.terrarium

import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthPulseSerializationTest {

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
    fun growthStateWrittenBeforeAppliedEchoHistoryStillBlocksItsLastEcho() {
        val legacy = Json.decodeFromString<GrowthState>(
            """{
              "instanceId":"rainbell-001",
              "growthProfileId":"botanical",
              "stageIndex":1,
              "growthPulsesApplied":1,
              "lastRelevantEchoId":"echo-legacy"
            }""".trimIndent()
        )

        assertTrue(legacy.appliedEchoIds.isEmpty())

        val replay = service.apply(layout, listOf(legacy), rain("echo-legacy"))

        assertEquals(listOf(legacy), replay)
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
