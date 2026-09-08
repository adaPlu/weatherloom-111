package com.rork.weatherloom.ui.terrarium

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrariumAnimationSourceContractTest {

    @Test
    fun `state projection and animation policy stay presentation only`() {
        val projection = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/terrarium/TerrariumVisualSnapshot.kt").readText()
        val policy = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/terrarium/TerrariumAnimationPolicy.kt").readText()
        val renderer = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/board/TerrariumScene.kt").readText()
        val presentation = projection + policy + renderer

        listOf(
            "SimulationEngine",
            "SaveData",
            "recordSolve(",
            "evaluateTerrariumReactions(",
            "TerrariumReactionSaveService",
            "TerrariumSaveService",
            "GrowthPulseService",
            "kotlin.random",
            "Random(",
            "System.currentTimeMillis",
            "Clock.System",
            "LaunchedEffect",
            "rememberCoroutineScope",
            "delay("
        ).forEach { forbidden ->
            assertFalse("Terrarium animation must remain deterministic presentation only; found $forbidden", presentation.contains(forbidden))
        }
    }

    @Test
    fun `navigation projects existing logical state into the presentation layer`() {
        val source = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/navigation/AppNavigation.kt").readText()

        assertTrue(source.contains("TerrariumVisualProjection"))
        assertTrue(source.contains("TerrariumAnimationPolicy"))
        assertTrue(source.contains("terrariumReactionSnapshot"))
    }

    @Test
    fun `repository exposes reaction presentation through an explicit read only accessor`() {
        val source = repoFile("android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt").readText()
        val marker = "fun terrariumReactionSnapshot()"
        val start = source.indexOf(marker)

        assertTrue("read-only reaction snapshot accessor is required", start >= 0)
        val body = source.substring(start, minOf(source.length, start + 1800))
        assertFalse(body.contains("mutator.mutate"))
        assertFalse(body.contains("persist("))
        assertFalse(body.contains("copy(terrarium"))
    }

    @Test
    fun `Terrarium screen exposes equivalent visible and semantic state indicators`() {
        val source = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/screens/TerrariumScreen.kt").readText()

        assertTrue(source.contains("stateIndicators"))
        assertTrue(source.contains("Terrarium state:"))
        assertTrue(source.contains("contentDescription"))
    }

    @Test
    fun `scene receives deterministic snapshot and animation policy instead of authoring state`() {
        val source = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/board/TerrariumScene.kt").readText()

        assertTrue(source.contains("TerrariumVisualSnapshot"))
        assertTrue(source.contains("TerrariumRenderPolicy"))
        assertFalse(source.contains("ReactionEngine"))
        assertFalse(source.contains("WeatherEchoSnapshot"))
        assertFalse(source.contains("GrowthState"))
    }

    private fun repoFile(relative: String): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val base = current ?: error("Could not locate repository file: $relative")
            val candidate = File(base, relative)
            if (candidate.isFile) return candidate
            current = base.parentFile
        }
        error("Could not locate repository file: $relative from ${System.getProperty("user.dir")}")
    }
}
