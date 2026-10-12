package com.rork.weatherloom.core.weather

import kotlinx.serialization.Serializable

const val WEATHER_ECHO_MAX_INTENSITY: Int = 2

/** Stable ordering used for durable snapshots and concise presentation tie-breaking. */
@Serializable
enum class WeatherEchoKind {
    Rain,
    Snow,
    Wind,
    Clear
}

/**
 * Compact deterministic summary of the weather actually produced by one puzzle run.
 *
 * Multiple physical phenomena may coexist. [Clear] is exclusive and is emitted only
 * by the deriver's explicit clear-condition contract. The Terrarium can persist this
 * value later without depending on mutable simulation/runtime objects.
 */
@Serializable
data class WeatherEchoSnapshot(
    val id: String,
    val kinds: List<WeatherEchoKind>,
    val rainIntensity: Int,
    val snowIntensity: Int,
    val windIntensity: Int,
    val primaryKind: WeatherEchoKind?
) {
    init {
        require(id.isNotBlank()) { "Weather Echo id must not be blank" }
        require(rainIntensity in 0..WEATHER_ECHO_MAX_INTENSITY) { "rainIntensity out of range" }
        require(snowIntensity in 0..WEATHER_ECHO_MAX_INTENSITY) { "snowIntensity out of range" }
        require(windIntensity in 0..WEATHER_ECHO_MAX_INTENSITY) { "windIntensity out of range" }
        require(kinds.distinct().size == kinds.size) { "Weather Echo kinds must be unique" }

        val canonicalOrder = WeatherEchoKind.entries.filter { it in kinds }
        require(kinds == canonicalOrder) { "Weather Echo kinds must use canonical ordering" }

        if (WeatherEchoKind.Clear in kinds) {
            require(kinds == listOf(WeatherEchoKind.Clear)) { "Clear cannot coexist with other weather" }
            require(rainIntensity == 0 && snowIntensity == 0 && windIntensity == 0) {
                "Clear cannot carry weather intensity"
            }
            require(primaryKind == WeatherEchoKind.Clear) { "Clear must be the primary kind" }
        } else {
            val expectedKinds = buildList {
                if (rainIntensity > 0) add(WeatherEchoKind.Rain)
                if (snowIntensity > 0) add(WeatherEchoKind.Snow)
                if (windIntensity > 0) add(WeatherEchoKind.Wind)
            }
            require(kinds == expectedKinds) {
                "Weather Echo kind membership must match positive intensities"
            }

            val expectedPrimary = listOf(
                WeatherEchoKind.Rain to rainIntensity,
                WeatherEchoKind.Snow to snowIntensity,
                WeatherEchoKind.Wind to windIntensity
            ).filter { (_, intensity) -> intensity > 0 }
                .maxByOrNull { (_, intensity) -> intensity }
                ?.first

            require(primaryKind == expectedPrimary) {
                "primaryKind must be the canonical highest-intensity weather kind"
            }
        }
    }
}
