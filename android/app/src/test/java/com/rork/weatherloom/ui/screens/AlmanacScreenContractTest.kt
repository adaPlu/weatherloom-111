package com.rork.weatherloom.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmanacScreenContractTest {

    @Test
    fun `screen renders all three Almanac sections`() {
        val source = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/screens/AlmanacScreen.kt").readText()

        assertTrue(source.contains("AlmanacSection.Species"))
        assertTrue(source.contains("AlmanacSection.Weather"))
        assertTrue(source.contains("AlmanacSection.Discoveries"))
    }

    @Test
    fun `navigation supplies durable Terrarium discoveries to Almanac`() {
        val source = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/navigation/AppNavigation.kt").readText()

        assertTrue(source.contains("terrariumDiscoveries"))
        assertTrue(source.contains("AlmanacProjection"))
    }

    @Test
    fun `Almanac projection and UI stay outside authoritative mutation paths`() {
        val almanacScreen = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/screens/AlmanacScreen.kt").readText()
        val projection = optionalRepoFile("android/app/src/main/java/com/rork/weatherloom/ui/almanac/AlmanacEntryViewData.kt")?.readText().orEmpty() +
            optionalRepoFile("android/app/src/main/java/com/rork/weatherloom/ui/almanac/AlmanacProjection.kt")?.readText().orEmpty()
        val combined = almanacScreen + projection

        listOf(
            "evaluateTerrariumReactions(",
            "terrariumDiscoveries =",
            "recordSolve(",
            "SimulationEngine",
            "PuzzleRewardBridge",
            "PlayerXpProgression",
            "TerrariumReactionSaveService",
            "kotlin.random",
            "System.currentTimeMillis",
            "Clock.System"
        ).forEach { forbidden ->
            assertFalse("Almanac must remain read-only and deterministic; found $forbidden", combined.contains(forbidden))
        }
    }

    @Test
    fun `player facing Weather copy is not derived from raw enum names`() {
        val projection = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/almanac/AlmanacProjection.kt").readText()

        assertFalse(projection.contains(".name"))
        assertFalse(projection.contains("valueOf("))
    }

    @Test
    fun `undiscovered UI has an explicit accessible label`() {
        val source = repoFile("android/app/src/main/java/com/rork/weatherloom/ui/screens/AlmanacScreen.kt").readText()

        assertTrue(source.contains("Undiscovered Almanac entry"))
    }

    private fun repoFile(relative: String): File = optionalRepoFile(relative)
        ?: error("Could not locate repository file: $relative from ${System.getProperty("user.dir")}")

    private fun optionalRepoFile(relative: String): File? {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val base = current ?: return null
            val candidate = File(base, relative)
            if (candidate.isFile) return candidate
            current = base.parentFile
        }
        return null
    }
}
