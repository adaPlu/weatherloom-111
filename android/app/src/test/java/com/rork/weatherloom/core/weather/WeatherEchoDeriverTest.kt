package com.rork.weatherloom.core.weather

import com.rork.weatherloom.core.sim.CausalEvent
import com.rork.weatherloom.core.sim.EventKind
import com.rork.weatherloom.core.sim.SimResult
import com.rork.weatherloom.core.sim.SimState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherEchoDeriverTest {

    @Test
    fun rainAndWindCanCoexistWithCanonicalOrderingAndTieBreaking() {
        val start = frame(size = 2, beat = 0)
        val rainy = frame(size = 2, beat = 1).apply {
            precip[0] = 2
            windStr[1] = 1
        }
        val windy = frame(size = 2, beat = 2).apply {
            windStr[0] = 2
        }

        val snapshot = WeatherEchoDeriver.derive(
            result(frames = listOf(start, rainy, windy))
        )

        assertEquals(listOf(WeatherEchoKind.Rain, WeatherEchoKind.Wind), snapshot.kinds)
        assertEquals(2, snapshot.rainIntensity)
        assertEquals(0, snapshot.snowIntensity)
        assertEquals(2, snapshot.windIntensity)
        assertEquals(WeatherEchoKind.Rain, snapshot.primaryKind)
        assertTrue(snapshot.id.startsWith("echo-v1-"))
    }

    @Test
    fun snowyPrecipitationIsNotCountedAsRain() {
        val start = frame(size = 1, beat = 0)
        val snow = frame(size = 1, beat = 1).apply {
            precip[0] = 2
            precipSnow[0] = 1
        }

        val snapshot = WeatherEchoDeriver.derive(result(listOf(start, snow)))

        assertEquals(listOf(WeatherEchoKind.Snow), snapshot.kinds)
        assertEquals(0, snapshot.rainIntensity)
        assertEquals(2, snapshot.snowIntensity)
        assertEquals(0, snapshot.windIntensity)
        assertEquals(WeatherEchoKind.Snow, snapshot.primaryKind)
    }

    @Test
    fun clearRequiresNoWeatherAndNoCloudOrFogInFinalFrame() {
        val clear = WeatherEchoDeriver.derive(
            result(listOf(frame(size = 1, beat = 0), frame(size = 1, beat = 1)))
        )
        assertEquals(listOf(WeatherEchoKind.Clear), clear.kinds)
        assertEquals(WeatherEchoKind.Clear, clear.primaryKind)

        val cloudyFinal = frame(size = 1, beat = 1).apply { cloud[0] = 1 }
        val neutral = WeatherEchoDeriver.derive(
            result(listOf(frame(size = 1, beat = 0), cloudyFinal))
        )
        assertTrue(neutral.kinds.isEmpty())
        assertNull(neutral.primaryKind)
    }

    @Test
    fun precipitationPreventsClearEvenWhenTheFinalFrameIsCalm() {
        val rain = frame(size = 1, beat = 1).apply { precip[0] = 1 }
        val finalCalm = frame(size = 1, beat = 2)

        val snapshot = WeatherEchoDeriver.derive(
            result(listOf(frame(size = 1, beat = 0), rain, finalCalm))
        )

        assertEquals(listOf(WeatherEchoKind.Rain), snapshot.kinds)
        assertEquals(WeatherEchoKind.Rain, snapshot.primaryKind)
    }

    @Test
    fun derivationIsDeterministicAndIndependentOfEventOrdering() {
        val start = frame(size = 1, beat = 0)
        val rain = frame(size = 1, beat = 1).apply { precip[0] = 1 }
        val eventsA = listOf(
            CausalEvent(1, EventKind.RainBegins, 0, "rain"),
            CausalEvent(1, EventKind.CloudsCollide, 0, "clouds")
        )
        val eventsB = eventsA.reversed()

        val first = WeatherEchoDeriver.derive(result(listOf(start, rain), eventsA))
        val second = WeatherEchoDeriver.derive(result(listOf(start, rain), eventsB))
        val repeated = WeatherEchoDeriver.derive(result(listOf(start, rain), eventsA))

        assertEquals(first, second)
        assertEquals(first, repeated)
    }

    @Test
    fun stableIdUsesOrderedFrameFingerprintsNotOnlyWeatherSummary() {
        val startA = frame(size = 1, beat = 0)
        val rainA = frame(size = 1, beat = 1).apply { precip[0] = 1 }
        val startB = frame(size = 1, beat = 0).apply { temp[0] = 1 }
        val rainB = frame(size = 1, beat = 1).apply { precip[0] = 1 }

        val first = WeatherEchoDeriver.derive(result(listOf(startA, rainA)))
        val second = WeatherEchoDeriver.derive(result(listOf(startB, rainB)))

        assertEquals(first.kinds, second.kinds)
        assertEquals(first.rainIntensity, second.rainIntensity)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun allThreePhenomenaRemainCanonicalAndMalformedIntensitiesAreBounded() {
        val start = frame(size = 1, beat = 0)
        val rainAndWind = frame(size = 1, beat = 1).apply {
            precip[0] = 99
            windStr[0] = 99
        }
        val snow = frame(size = 1, beat = 2).apply {
            precip[0] = 99
            precipSnow[0] = 1
        }

        val snapshot = WeatherEchoDeriver.derive(result(listOf(start, rainAndWind, snow)))

        assertEquals(
            listOf(WeatherEchoKind.Rain, WeatherEchoKind.Snow, WeatherEchoKind.Wind),
            snapshot.kinds
        )
        assertEquals(WEATHER_ECHO_MAX_INTENSITY, snapshot.rainIntensity)
        assertEquals(WEATHER_ECHO_MAX_INTENSITY, snapshot.snowIntensity)
        assertEquals(WEATHER_ECHO_MAX_INTENSITY, snapshot.windIntensity)
        assertEquals(WeatherEchoKind.Rain, snapshot.primaryKind)
    }

    @Test
    fun changingFrameOrderChangesStableId() {
        val start = frame(size = 1, beat = 0)
        val warm = frame(size = 1, beat = 1).apply { temp[0] = 1 }
        val moist = frame(size = 1, beat = 2).apply { moisture[0] = 1 }

        val first = WeatherEchoDeriver.derive(result(listOf(start, warm, moist)))
        val reordered = WeatherEchoDeriver.derive(result(listOf(start, moist, warm)))

        assertEquals(first.kinds, reordered.kinds)
        assertNotEquals(first.id, reordered.id)
    }

    @Test
    fun emptyFrameSequenceIsRejected() {
        val invalid = SimResult(
            frames = emptyList(),
            events = emptyList(),
            solved = false,
            progress = emptyList(),
            endHash = 0L
        )

        assertIllegalArgument { WeatherEchoDeriver.derive(invalid) }
    }

    @Test
    fun inconsistentFrameSizesAreRejected() {
        val invalid = result(
            listOf(
                frame(size = 1, beat = 0),
                frame(size = 2, beat = 1)
            )
        )

        assertIllegalArgument { WeatherEchoDeriver.derive(invalid) }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    private fun frame(size: Int, beat: Int): SimState =
        SimState(size).apply { this.beat = beat }

    private fun result(
        frames: List<SimState>,
        events: List<CausalEvent> = emptyList()
    ): SimResult = SimResult(
        frames = frames,
        events = events,
        solved = false,
        progress = emptyList(),
        endHash = frames.lastOrNull()?.hash() ?: 0L
    )
}
