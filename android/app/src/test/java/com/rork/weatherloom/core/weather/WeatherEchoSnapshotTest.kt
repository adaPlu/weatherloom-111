package com.rork.weatherloom.core.weather

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherEchoSnapshotTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun serializableSnapshotRoundTripsWithoutChangingDurableFields() {
        val expected = WeatherEchoSnapshot(
            id = "echo-v1-deadbeef",
            kinds = listOf(WeatherEchoKind.Rain, WeatherEchoKind.Wind),
            rainIntensity = 2,
            snowIntensity = 0,
            windIntensity = 1,
            primaryKind = WeatherEchoKind.Rain
        )

        val encoded = json.encodeToString(WeatherEchoSnapshot.serializer(), expected)
        val decoded = json.decodeFromString(WeatherEchoSnapshot.serializer(), encoded)

        assertEquals(expected, decoded)
    }

    @Test
    fun nonCanonicalKindOrderingIsRejected() {
        assertIllegalArgument {
            WeatherEchoSnapshot(
                id = "echo-v1-order",
                kinds = listOf(WeatherEchoKind.Wind, WeatherEchoKind.Rain),
                rainIntensity = 1,
                snowIntensity = 0,
                windIntensity = 1,
                primaryKind = WeatherEchoKind.Rain
            )
        }
    }

    @Test
    fun clearCannotCoexistWithOtherWeatherOrIntensity() {
        assertIllegalArgument {
            WeatherEchoSnapshot(
                id = "echo-v1-conflict",
                kinds = listOf(WeatherEchoKind.Rain, WeatherEchoKind.Clear),
                rainIntensity = 1,
                snowIntensity = 0,
                windIntensity = 0,
                primaryKind = WeatherEchoKind.Rain
            )
        }

        assertIllegalArgument {
            WeatherEchoSnapshot(
                id = "echo-v1-clear-intensity",
                kinds = listOf(WeatherEchoKind.Clear),
                rainIntensity = 0,
                snowIntensity = 0,
                windIntensity = 1,
                primaryKind = WeatherEchoKind.Clear
            )
        }
    }

    @Test
    fun primaryKindMustBelongToSnapshotKinds() {
        assertIllegalArgument {
            WeatherEchoSnapshot(
                id = "echo-v1-primary",
                kinds = listOf(WeatherEchoKind.Snow),
                rainIntensity = 0,
                snowIntensity = 1,
                windIntensity = 0,
                primaryKind = WeatherEchoKind.Wind
            )
        }
    }

    @Test
    fun kindMembershipMustMatchPositiveIntensities() {
        assertIllegalArgument {
            WeatherEchoSnapshot(
                id = "echo-v1-zero-rain",
                kinds = listOf(WeatherEchoKind.Rain),
                rainIntensity = 0,
                snowIntensity = 0,
                windIntensity = 0,
                primaryKind = WeatherEchoKind.Rain
            )
        }

        assertIllegalArgument {
            WeatherEchoSnapshot(
                id = "echo-v1-hidden-wind",
                kinds = listOf(WeatherEchoKind.Rain),
                rainIntensity = 1,
                snowIntensity = 0,
                windIntensity = 2,
                primaryKind = WeatherEchoKind.Rain
            )
        }
    }

    @Test
    fun primaryKindMustBeCanonicalHighestIntensity() {
        assertIllegalArgument {
            WeatherEchoSnapshot(
                id = "echo-v1-wrong-primary",
                kinds = listOf(WeatherEchoKind.Rain, WeatherEchoKind.Wind),
                rainIntensity = 1,
                snowIntensity = 0,
                windIntensity = 2,
                primaryKind = WeatherEchoKind.Rain
            )
        }

        assertIllegalArgument {
            WeatherEchoSnapshot(
                id = "echo-v1-wrong-tie",
                kinds = listOf(WeatherEchoKind.Rain, WeatherEchoKind.Snow),
                rainIntensity = 2,
                snowIntensity = 2,
                windIntensity = 0,
                primaryKind = WeatherEchoKind.Snow
            )
        }
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
}
