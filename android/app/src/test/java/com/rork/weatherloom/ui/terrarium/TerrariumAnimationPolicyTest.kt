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

class TerrariumAnimationPolicyTest {

    @Test
    fun `normal motion policy follows logical presentation state`() {
        val snapshot = snapshot()
        val policy = policy(snapshot, reducedMotion = false)

        assertTrue(policy.boolean("getAnimateRain"))
        assertFalse(policy.boolean("getAnimateSnow"))
        assertTrue(policy.boolean("getAnimateWind"))
        assertTrue(policy.boolean("getAnimateVegetation"))
        assertTrue(policy.boolean("getAnimateVisitors"))
    }

    @Test
    fun `Reduced Motion preserves equivalent static state communication`() {
        val snapshot = snapshot()
        val normal = policy(snapshot, reducedMotion = false)
        val reduced = policy(snapshot, reducedMotion = true)
        val labels = snapshot.stringList("getStateLabels")

        assertFalse(reduced.boolean("getAnimateRain"))
        assertFalse(reduced.boolean("getAnimateSnow"))
        assertFalse(reduced.boolean("getAnimateWind"))
        assertFalse(reduced.boolean("getAnimateVegetation"))
        assertFalse(reduced.boolean("getAnimateVisitors"))
        assertEquals(labels, normal.stringList("getStateIndicators"))
        assertEquals(labels, reduced.stringList("getStateIndicators"))
        assertTrue(reduced.stringList("getStateIndicators").contains("Rain"))
        assertTrue(reduced.stringList("getStateIndicators").contains("Wind"))
        assertTrue(reduced.stringList("getStateIndicators").any { it.contains("Butterfly", ignoreCase = true) })
    }

    @Test
    fun `animation policy is deterministic`() {
        val snapshot = snapshot()

        assertEquals(policy(snapshot, false), policy(snapshot, false))
        assertEquals(policy(snapshot, true), policy(snapshot, true))
    }

    @Test
    fun `no visitor means visitor animation is disabled`() {
        val noVisitor = snapshot(ReactionResult(
            visualStates = listOf(ReactionVisualState("specimen-rainbell", listOf("bloom", "wet")))
        ))

        assertFalse(policy(noVisitor, false).boolean("getAnimateVisitors"))
    }

    private fun snapshot(reactions: ReactionResult = reactions()): Any {
        val projectionClass = requiredClass("com.rork.weatherloom.ui.terrarium.TerrariumVisualProjection")
        val instance = projectionClass.getField("INSTANCE").get(null)
        return projectionClass.getMethod(
            "project",
            List::class.java,
            TerrariumLayout::class.java,
            List::class.java,
            EnvironmentState::class.java,
            ReactionResult::class.java,
            TerrariumCatalog::class.java
        ).invoke(
            instance,
            listOf("windreed", "rainbell"),
            layout,
            growth,
            environment,
            reactions,
            catalog
        )
    }

    private fun policy(snapshot: Any, reducedMotion: Boolean): Any {
        val policyClass = requiredClass("com.rork.weatherloom.ui.terrarium.TerrariumAnimationPolicy")
        val snapshotClass = requiredClass("com.rork.weatherloom.ui.terrarium.TerrariumVisualSnapshot")
        val instance = policyClass.getField("INSTANCE").get(null)
        return policyClass.getMethod(
            "forSnapshot",
            snapshotClass,
            Boolean::class.javaPrimitiveType
        ).invoke(instance, snapshot, reducedMotion)
    }

    private fun requiredClass(name: String): Class<*> = try {
        Class.forName(name)
    } catch (error: ClassNotFoundException) {
        fail("Required Terrarium animation type is missing: $name")
        throw AssertionError(error)
    }

    private fun Any.boolean(getter: String): Boolean = javaClass.getMethod(getter).invoke(this) as Boolean

    @Suppress("UNCHECKED_CAST")
    private fun Any.stringList(getter: String): List<String> =
        javaClass.getMethod(getter).invoke(this) as List<String>

    companion object {
        private val footprint = TerrariumFootprint(1, 1)
        private val catalog = TerrariumCatalog(
            schemaVersion = 1,
            growthProfiles = listOf(GrowthProfile("rainbell-growth", GrowthStage.entries.toList())),
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
                    allowedRotations = listOf(TerrariumRotation.Deg0)
                )
            )
        )
        private val layout = TerrariumLayout(
            listOf(
                TerrariumPlacement(
                    instanceId = "specimen-rainbell",
                    itemId = "rainbell",
                    xNormalized = 0.4f,
                    yNormalized = 0.6f,
                    logicalFootprint = footprint
                )
            )
        )
        private val growth = listOf(
            GrowthState(
                instanceId = "specimen-rainbell",
                growthProfileId = "rainbell-growth",
                stageIndex = GrowthStage.Bloom.ordinal
            )
        )
        private val environment = EnvironmentState(
            WeatherEchoSnapshot(
                id = "echo-rain-wind",
                kinds = listOf(WeatherEchoKind.Rain, WeatherEchoKind.Wind),
                rainIntensity = 2,
                snowIntensity = 0,
                windIntensity = 1,
                primaryKind = WeatherEchoKind.Rain
            )
        )

        private fun reactions() = ReactionResult(
            visualStates = listOf(ReactionVisualState("specimen-rainbell", listOf("bloom", "wet"))),
            visitors = listOf(VisitorPresence("butterfly", "rainbell-after-rain", "specimen-rainbell"))
        )
    }
}
