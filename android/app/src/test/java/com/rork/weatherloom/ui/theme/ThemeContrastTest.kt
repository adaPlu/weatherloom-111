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
