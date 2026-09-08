package com.rork.weatherloom.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {

    @Test
    fun semanticSmallTextColorsMeetWcagAaAcrossWarmSurfaces() {
        val backgrounds = listOf(Loom.Canvas, Loom.Surface, Loom.SurfaceSunk)
        val textColors = listOf(
            Loom.TextMuted,
            Loom.TextAccent,
            Loom.TextCold,
            Loom.TextMoisture
        )

        textColors.forEach { foreground ->
            backgrounds.forEach { background ->
                assertTrue(
                    "Expected >= 4.5:1 contrast, got ${contrastRatio(foreground, background)}",
                    contrastRatio(foreground, background) >= 4.5
                )
            }
        }
    }

    @Test
    fun actionColorsSupportSmallLightText() {
        listOf(
            Loom.CoralStrong,
            Loom.ColdStrong,
            Loom.MoistureStrong
        ).forEach { container ->
            assertTrue(
                "Action container must support small light text",
                contrastRatio(container, Loom.Surface) >= 4.5
            )
        }
    }

    @Test
    fun highContrastPreferenceIsReactiveAtAppThemeBoundary() {
        val theme = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/ui/theme/Theme.kt"
        ).readText()
        val activity = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/MainActivity.kt"
        ).readText()

        assertTrue(theme.contains("HighContrastLoomColorScheme"))
        assertTrue(theme.contains("fun AppTheme(highContrast: Boolean"))
        assertTrue(theme.contains("if (highContrast)"))
        assertTrue(activity.contains("collectAsStateWithLifecycle"))
        assertTrue(activity.contains("AppTheme(highContrast = save.highContrast)"))
    }

    @Test
    fun highContrastPreferenceIsExposedInComfortSettings() {
        val almanac = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/ui/screens/AlmanacScreen.kt"
        ).readText()
        val navigation = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/ui/navigation/AppNavigation.kt"
        ).readText()

        assertTrue(almanac.contains("highContrast: Boolean"))
        assertTrue(almanac.contains("onHighContrast: (Boolean) -> Unit"))
        assertTrue(almanac.contains("title = \"High contrast\""))
        assertTrue(almanac.contains("checked = highContrast"))
        assertTrue(navigation.contains("highContrast = save.highContrast"))
        assertTrue(navigation.contains("onHighContrast = repo::setHighContrast"))
    }

    @Test
    fun sharedSmallTextPathsUseAccessibleSemanticColors() {
        val shared = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/ui/components/LoomComponents.kt"
        ).readText()
        val puzzle = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/ui/puzzle/PuzzleScreen.kt"
        ).readText()
        val navigation = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/ui/navigation/AppNavigation.kt"
        ).readText()

        assertFalse(shared.contains("Loom.Moss"))
        assertFalse(puzzle.contains("Loom.Moss"))
        assertFalse(navigation.contains("Loom.Moss"))
        assertFalse(
            "Primary action must not use the low-contrast decorative coral",
            Regex("""container:\s*Color\s*=\s*Loom\.Coral\s*[,)]""").containsMatchIn(shared)
        )
        assertFalse("Puzzle small labels must not use decorative coral as text", puzzle.contains("color = Loom.Coral"))
    }

    @Test
    fun allTextHeavyScreensAvoidRawLowContrastMossForegrounds() {
        val paths = listOf(
            "android/app/src/main/java/com/rork/weatherloom/ui/screens/AlmanacScreen.kt",
            "android/app/src/main/java/com/rork/weatherloom/ui/screens/DailyScreen.kt",
            "android/app/src/main/java/com/rork/weatherloom/ui/screens/LevelsScreen.kt",
            "android/app/src/main/java/com/rork/weatherloom/ui/screens/TerrariumScreen.kt",
            "android/app/src/main/java/com/rork/weatherloom/ui/puzzle/ResultSheet.kt"
        )

        paths.forEach { path ->
            val source = repoFile(path).readText()
            assertFalse(
                "$path still uses decorative Loom.Moss as a foreground",
                source.contains("Loom.Moss")
            )
        }
    }

    @Test
    fun dailySmallLightTextUsesStrongContainers() {
        val daily = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/ui/screens/DailyScreen.kt"
        ).readText()

        assertTrue(daily.contains("container = if (completedToday) Loom.MoistureStrong else Loom.CoralStrong"))
        assertTrue(daily.contains("done -> Loom.MoistureStrong"))
        assertFalse(daily.contains("container = if (completedToday) Loom.Moisture else Loom.Coral"))
    }

    @Test
    fun chapterBadgesUseStrongSurfacesForSmallLightText() {
        val levels = repoFile(
            "android/app/src/main/java/com/rork/weatherloom/ui/screens/LevelsScreen.kt"
        ).readText()

        assertTrue(levels.contains("1 -> Loom.CoralStrong"))
        assertTrue(levels.contains("2 -> Loom.WindStrong"))
        assertTrue(levels.contains("3 -> Loom.ColdStrong"))
        assertTrue(levels.contains("4 -> Loom.OchreStrong"))
        assertTrue(levels.contains("5 -> Loom.MoistureStrong"))
        assertTrue(levels.contains("else -> Loom.PurpleStrong"))
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linear(component: Float): Double {
            val c = component.toDouble()
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(8) {
            val candidate = File(dir, path)
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not resolve repository file: $path")
    }
}
