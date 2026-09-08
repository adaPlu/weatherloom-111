package com.rork.weatherloom.ui.terrarium

import com.rork.weatherloom.core.terrarium.GrowthProfile
import com.rork.weatherloom.core.terrarium.GrowthStage
import com.rork.weatherloom.core.terrarium.GrowthState
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumCategory
import com.rork.weatherloom.core.terrarium.TerrariumFootprint
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumLayout
import com.rork.weatherloom.core.terrarium.TerrariumPlacement
import com.rork.weatherloom.core.terrarium.TerrariumRotation
import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.terrarium.reaction.ReactionResult
import com.rork.weatherloom.core.terrarium.reaction.ReactionVisualState
import com.rork.weatherloom.core.terrarium.reaction.VisitorPresence
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TerrariumVisualSnapshotTest {

    @Test
    fun `equivalent logical state produces equivalent visual snapshot`() {
        val first = project(rainEnvironment(), fullLayout(), fullGrowth(), rainReactions(), listOf("rainbell", "windreed"))
        val second = project(rainEnvironment(), fullLayout(), fullGrowth(), rainReactions(), listOf("rainbell", "windreed"))

        assertEquals(first, second)
    }

    @Test
    fun `input collection ordering cannot change snapshot output`() {
        val forward = project(
            rainEnvironment(),
            fullLayout(),
            fullGrowth(),
            rainReactions(),
            listOf("rainbell", "windreed")
        )
        val reverse = project(
            rainEnvironment(),
            TerrariumLayout(fullLayout().placements.reversed()),
            fullGrowth().reversed(),
            rainReactions(),
            listOf("windreed", "rainbell")
        )

        assertEquals(forward, reverse)
        assertEquals(listOf("rainbell", "windreed"), forward.stringList("getVisibleItemIds"))
    }

    @Test
    fun `rain state maps to rain presentation`() {
        val snapshot = project(rainEnvironment())

        assertEquals(2, snapshot.int("getRainIntensity"))
        assertEquals(0, snapshot.int("getSnowIntensity"))
        assertTrue(snapshot.stringList("getStateLabels").contains("Rain"))
    }

    @Test
    fun `snow state maps to snow presentation`() {
        val snapshot = project(snowEnvironment())

        assertEquals(2, snapshot.int("getSnowIntensity"))
        assertEquals(0, snapshot.int("getRainIntensity"))
        assertTrue(snapshot.stringList("getStateLabels").contains("Snow"))
    }

    @Test
    fun `wind state maps to wind presentation`() {
        val snapshot = project(windEnvironment())

        assertEquals(2, snapshot.int("getWindIntensity"))
        assertTrue(snapshot.stringList("getStateLabels").contains("Wind"))
    }

    @Test
    fun `Rainbell growth and reaction state map into one deterministic specimen state`() {
        val snapshot = project(
            rainEnvironment(),
            fullLayout(),
            fullGrowth(),
            rainReactions(),
            listOf("rainbell", "windreed")
        )
        val specimens = snapshot.objectList("getSpecimens")
        val rainbell = specimens.single { it.string("getItemId") == "rainbell" }

        assertEquals(GrowthStage.Bloom, rainbell.any("getGrowthStage"))
        assertEquals(listOf("bloom", "wet"), rainbell.stringList("getVisualTags"))
        assertTrue(snapshot.stringList("getStateLabels").any { it.contains("Rainbell", ignoreCase = true) })
    }

    @Test
    fun `butterfly visitor presence maps to presentation`() {
        val snapshot = project(
            rainEnvironment(),
            fullLayout(),
            fullGrowth(),
            rainReactions(),
            listOf("rainbell", "windreed")
        )
        val visitors = snapshot.objectList("getVisitors")

        assertEquals(1, visitors.size)
        assertEquals("butterfly", visitors.single().string("getVisitorId"))
        assertTrue(snapshot.stringList("getStateLabels").any { it.contains("Butterfly", ignoreCase = true) })
    }

    @Test
    fun `absent visitor does not render visitor presence`() {
        val snapshot = project(
            rainEnvironment(),
            fullLayout(),
            fullGrowth(),
            ReactionResult(),
            listOf("rainbell", "windreed")
        )

        assertTrue(snapshot.objectList("getVisitors").isEmpty())
        assertFalse(snapshot.stringList("getStateLabels").any { it.contains("Butterfly", ignoreCase = true) })
    }

    @Test
    fun `presentation projection does not mutate authoritative input state`() {
        val layout = fullLayout()
        val growth = fullGrowth()
        val reactions = rainReactions()
        val unlocked = mutableListOf("windreed", "rainbell")
        val layoutBefore = layout.copy(placements = layout.placements.toList())
        val growthBefore = growth.toList()
        val reactionsBefore = reactions.copy(
            visualStates = reactions.visualStates.toList(),
            visitors = reactions.visitors.toList()
        )
        val unlockedBefore = unlocked.toList()

        project(rainEnvironment(), layout, growth, reactions, unlocked)

        assertEquals(layoutBefore, layout)
        assertEquals(growthBefore, growth)
        assertEquals(reactionsBefore, reactions)
        assertEquals(unlockedBefore, unlocked)
    }

    @Test
    fun `player facing snapshot labels never expose raw internal ids`() {
        val snapshot = project(
            rainEnvironment(),
            fullLayout(),
            fullGrowth(),
            rainReactions(),
            listOf("rainbell", "windreed")
        )
        val copy = snapshot.stringList("getStateLabels").joinToString(" ")

        assertFalse(copy.contains("rainbell_after_rain"))
        assertFalse(copy.contains("specimen-rainbell"))
        assertFalse(copy.contains("_"))
    }

    private fun project(
        environment: EnvironmentState? = null,
        layout: TerrariumLayout = TerrariumLayout(),
        growth: List<GrowthState> = emptyList(),
        reactions: ReactionResult = ReactionResult(),
        unlocked: List<String> = listOf("rainbell", "windreed")
    ): Any {
        val projectionClass = requiredClass("com.rork.weatherloom.ui.terrarium.TerrariumVisualProjection")
        val instance = try {
            projectionClass.getField("INSTANCE").get(null)
        } catch (error: Throwable) {
            fail("TerrariumVisualProjection must be a Kotlin object: ${error.message}")
            throw AssertionError(error)
        }
        return try {
            projectionClass.getMethod(
                "project",
                List::class.java,
                TerrariumLayout::class.java,
                List::class.java,
                EnvironmentState::class.java,
                ReactionResult::class.java,
                TerrariumCatalog::class.java
            ).invoke(instance, unlocked, layout, growth, environment, reactions, catalog)
        } catch (error: Throwable) {
            fail("TerrariumVisualProjection.project must satisfy the deterministic read-model contract: ${error.cause?.message ?: error.message}")
            throw AssertionError(error)
        }
    }

    private fun rainEnvironment() = EnvironmentState(
        WeatherEchoSnapshot(
            id = "echo-rain",
            kinds = listOf(WeatherEchoKind.Rain),
            rainIntensity = 2,
            snowIntensity = 0,
            windIntensity = 0,
            primaryKind = WeatherEchoKind.Rain
        )
    )

    private fun snowEnvironment() = EnvironmentState(
        WeatherEchoSnapshot(
            id = "echo-snow",
            kinds = listOf(WeatherEchoKind.Snow),
            rainIntensity = 0,
            snowIntensity = 2,
            windIntensity = 0,
            primaryKind = WeatherEchoKind.Snow
        )
    )

    private fun windEnvironment() = EnvironmentState(
        WeatherEchoSnapshot(
            id = "echo-wind",
            kinds = listOf(WeatherEchoKind.Wind),
            rainIntensity = 0,
            snowIntensity = 0,
            windIntensity = 2,
            primaryKind = WeatherEchoKind.Wind
        )
    )

    private fun fullLayout() = TerrariumLayout(
        listOf(
            TerrariumPlacement(
                instanceId = "specimen-rainbell",
                itemId = "rainbell",
                xNormalized = 0.40f,
                yNormalized = 0.60f,
                logicalFootprint = footprint
            ),
            TerrariumPlacement(
                instanceId = "specimen-windreed",
                itemId = "windreed",
                xNormalized = 0.60f,
                yNormalized = 0.60f,
                logicalFootprint = footprint
            )
        )
    )

    private fun fullGrowth() = listOf(
        GrowthState(
            instanceId = "specimen-rainbell",
            growthProfileId = "rainbell-growth",
            stageIndex = GrowthStage.Bloom.ordinal
        ),
        GrowthState(
            instanceId = "specimen-windreed",
            growthProfileId = "reed-growth",
            stageIndex = GrowthStage.Mature.ordinal
        )
    )

    private fun rainReactions() = ReactionResult(
        visualStates = listOf(
            ReactionVisualState("specimen-rainbell", listOf("bloom", "wet"))
        ),
        visitors = listOf(
            VisitorPresence("butterfly", "rainbell-after-rain", "specimen-rainbell")
        )
    )

    private fun requiredClass(name: String): Class<*> = try {
        Class.forName(name)
    } catch (error: ClassNotFoundException) {
        fail("Required Terrarium presentation type is missing: $name")
        throw AssertionError(error)
    }

    private fun Any.any(getter: String): Any? = javaClass.getMethod(getter).invoke(this)
    private fun Any.int(getter: String): Int = any(getter) as Int
    private fun Any.string(getter: String): String = any(getter) as String

    @Suppress("UNCHECKED_CAST")
    private fun Any.stringList(getter: String): List<String> = any(getter) as List<String>

    @Suppress("UNCHECKED_CAST")
    private fun Any.objectList(getter: String): List<Any> = any(getter) as List<Any>

    companion object {
        private val footprint = TerrariumFootprint(1, 1)
        private val catalog = TerrariumCatalog(
            schemaVersion = 1,
            growthProfiles = listOf(
                GrowthProfile("rainbell-growth", GrowthStage.entries.toList()),
                GrowthProfile("reed-growth", GrowthStage.entries.toList())
            ),
            items = listOf(
                TerrariumItem(
                    id = "rainbell",
                    nameKey = "Rainbell",
                    category = TerrariumCategory.Botanical,
                    visualFamily = "botanical",
                    footprint = footprint,
                    allowedRotations = listOf(TerrariumRotation.Deg0),
                    reactionTags = listOf("rainbell"),
                    growthProfileId = "rainbell-growth"
                ),
                TerrariumItem(
                    id = "windreed",
                    nameKey = "Windreed",
                    category = TerrariumCategory.Botanical,
                    visualFamily = "botanical",
                    footprint = footprint,
                    allowedRotations = listOf(TerrariumRotation.Deg0),
                    growthProfileId = "reed-growth"
                )
            )
        )
    }
}
