package com.rork.weatherloom.ui.almanac

import com.rork.weatherloom.core.level.Collectible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AlmanacEntryViewDataTest {

    @Test
    fun `almanac exposes species weather and discoveries sections`() {
        assertEquals(
            listOf("Species", "Weather", "Discoveries"),
            enumNames("com.rork.weatherloom.ui.almanac.AlmanacSection")
        )
    }

    @Test
    fun `existing discovered species content remains available`() {
        val rainbell = Collectible(
            id = "rainbell",
            name = "Rainbell",
            flavour = "A bell-shaped flower that wakes with rain.",
            unlock = "Let rain reach a meadow bloom.",
            biome = "Meadow"
        )

        val entry = species(listOf(rainbell), setOf("rainbell")).single()

        assertEquals("rainbell", entry.string("getId"))
        assertEquals("Rainbell", entry.string("getTitle"))
        assertTrue(entry.string("getBody").contains("wakes with rain"))
        assertEquals("Meadow", entry.nullableString("getLabel"))
        assertTrue(entry.boolean("getDiscovered"))
    }

    @Test
    fun `undiscovered species keeps authored clue without exposing id`() {
        val hidden = Collectible(
            id = "cloudmoss_internal",
            name = "Cloudmoss",
            flavour = "Soft moss that gathers mist.",
            unlock = "Look where fog settles low.",
            biome = "Wetland"
        )

        val entry = species(listOf(hidden), emptySet()).single()
        val visible = entry.visibleText()

        assertFalse(visible.contains("cloudmoss_internal"))
        assertTrue(visible.contains("Look where fog settles low."))
        assertFalse(entry.boolean("getDiscovered"))
    }

    @Test
    fun `known durable rainbell discovery maps to authored player facing content`() {
        val entry = discoveries(setOf("rainbell_after_rain"))
            .single { it.string("getId") == "rainbell_after_rain" }
        val visible = entry.visibleText()

        assertTrue(entry.boolean("getDiscovered"))
        assertTrue(visible.contains("Rainbell", ignoreCase = true))
        assertTrue(visible.contains("rain", ignoreCase = true))
        assertFalse(visible.contains("rainbell_after_rain"))
        assertFalse(visible.contains("_"))
    }

    @Test
    fun `undiscovered authored discovery shows intentional clue and no raw id`() {
        val entry = discoveries(emptySet())
            .single { it.string("getId") == "rainbell_after_rain" }
        val clue = entry.nullableString("getClue")
        val visible = entry.visibleText()

        assertFalse(entry.boolean("getDiscovered"))
        assertNotNull(clue)
        assertTrue(clue!!.isNotBlank())
        assertFalse(visible.contains("rainbell_after_rain"))
        assertFalse(visible.contains("_"))
    }

    @Test
    fun `unknown future discovery ids degrade safely and never render raw strings`() {
        val baseline = discoveries(emptySet())
        val withUnknown = discoveries(linkedSetOf("future_secret_9000", "unknown.discovery"))

        assertEquals(ids(baseline), ids(withUnknown))
        assertFalse(withUnknown.joinToString(" ") { it.visibleText() }.contains("future_secret_9000"))
        assertFalse(withUnknown.joinToString(" ") { it.visibleText() }.contains("unknown.discovery"))
    }

    @Test
    fun `species ordering is deterministic regardless of input order`() {
        val alpha = Collectible("alpha", "Alpha", "A", "Find A", "Meadow")
        val zeta = Collectible("zeta", "Zeta", "Z", "Find Z", "Forest")

        val forward = species(listOf(alpha, zeta), setOf("alpha", "zeta"))
        val reverse = species(listOf(zeta, alpha), setOf("zeta", "alpha"))

        assertEquals(listOf("alpha", "zeta"), ids(forward))
        assertEquals(ids(forward), ids(reverse))
    }

    @Test
    fun `weather ordering is deterministic and authored`() {
        val first = weather()
        val second = weather()

        assertEquals(ids(first), ids(second))
        assertEquals(listOf("weather.rain", "weather.snow", "weather.wind", "weather.clear"), ids(first))
        assertEquals(listOf("Rain", "Snow", "Wind", "Clear skies"), first.map { it.string("getTitle") })

        first.forEach { entry ->
            val body = entry.string("getBody")
            assertTrue(body.length >= 20)
            assertFalse(body.contains("WeatherEchoKind"))
            assertFalse(body.contains("_"))
            assertNotEquals(entry.string("getTitle"), body)
        }
    }

    @Test
    fun `weather copy does not invent a weather history system`() {
        val forbiddenClaims = listOf("history", "historical", "past puzzles", "previous puzzles", "tracked weather")
        val copy = weather().joinToString(" ") { it.visibleText() }.lowercase()

        forbiddenClaims.forEach { phrase -> assertFalse("weather copy must not claim '$phrase'", copy.contains(phrase)) }
    }

    @Test
    fun `discoveries ordering is deterministic regardless of input collection order`() {
        val first = discoveries(linkedSetOf("rainbell_after_rain", "future_two", "future_one"))
        val second = discoveries(linkedSetOf("future_one", "future_two", "rainbell_after_rain"))

        assertEquals(ids(first), ids(second))
        assertEquals(first.map { it.visibleText() }, second.map { it.visibleText() })
    }

    private fun species(collectibles: List<Collectible>, discovered: Set<String>): List<Any> =
        invokeProjection("species", arrayOf(List::class.java, Set::class.java), arrayOf(collectibles, discovered))

    private fun weather(): List<Any> = invokeProjection("weather", emptyArray(), emptyArray())

    private fun discoveries(discovered: Set<String>): List<Any> =
        invokeProjection("discoveries", arrayOf(Set::class.java), arrayOf(discovered))

    @Suppress("UNCHECKED_CAST")
    private fun invokeProjection(name: String, parameterTypes: Array<Class<*>>, args: Array<Any>): List<Any> {
        val clazz = requiredClass("com.rork.weatherloom.ui.almanac.AlmanacProjection")
        val instance = try {
            clazz.getField("INSTANCE").get(null)
        } catch (error: Throwable) {
            fail("AlmanacProjection must be a Kotlin object: ${error.message}")
            throw AssertionError(error)
        }
        return try {
            clazz.getMethod(name, *parameterTypes).invoke(instance, *args) as List<Any>
        } catch (error: Throwable) {
            fail("AlmanacProjection.$name must satisfy the read-model contract: ${error.cause?.message ?: error.message}")
            throw AssertionError(error)
        }
    }

    private fun enumNames(className: String): List<String> =
        requiredClass(className).enumConstants.map { (it as Enum<*>).name }

    private fun requiredClass(name: String): Class<*> = try {
        Class.forName(name)
    } catch (error: ClassNotFoundException) {
        fail("Required Almanac read-model type is missing: $name")
        throw AssertionError(error)
    }

    private fun ids(entries: List<Any>): List<String> = entries.map { it.string("getId") }

    private fun Any.string(getter: String): String =
        javaClass.getMethod(getter).invoke(this) as String

    private fun Any.nullableString(getter: String): String? =
        javaClass.getMethod(getter).invoke(this) as String?

    private fun Any.boolean(getter: String): Boolean =
        javaClass.getMethod(getter).invoke(this) as Boolean

    private fun Any.visibleText(): String = listOfNotNull(
        nullableString("getTitle"),
        nullableString("getBody"),
        nullableString("getLabel"),
        nullableString("getClue")
    ).joinToString(" ")
}
