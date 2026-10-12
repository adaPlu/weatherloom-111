package com.rork.weatherloom.core.weather

import com.rork.weatherloom.core.sim.SIM_VERSION
import com.rork.weatherloom.core.sim.SimResult
import com.rork.weatherloom.core.sim.SimState

/** Pure deterministic derivation from frozen simulation frames into Terrarium weather input. */
object WeatherEchoDeriver {

    fun derive(result: SimResult): WeatherEchoSnapshot {
        require(result.frames.isNotEmpty()) { "Weather Echo derivation requires at least one frame" }

        val expectedSize = result.frames.first().size
        require(result.frames.all { it.size == expectedSize }) {
            "Weather Echo derivation requires a consistent frame size"
        }

        var rainIntensity = 0
        var snowIntensity = 0
        var windIntensity = 0

        for (frame in result.frames) {
            for (cell in 0 until frame.size) {
                val precipitation = frame.precip[cell].coerceIn(0, WEATHER_ECHO_MAX_INTENSITY)
                if (frame.precipSnow[cell] > 0) {
                    snowIntensity = maxOf(snowIntensity, precipitation)
                } else {
                    rainIntensity = maxOf(rainIntensity, precipitation)
                }

                windIntensity = maxOf(
                    windIntensity,
                    frame.windStr[cell].coerceIn(0, WEATHER_ECHO_MAX_INTENSITY)
                )
            }
        }

        val kinds = buildList {
            if (rainIntensity > 0) add(WeatherEchoKind.Rain)
            if (snowIntensity > 0) add(WeatherEchoKind.Snow)
            if (windIntensity > 0) add(WeatherEchoKind.Wind)

            if (isClear(result.finalState, rainIntensity, snowIntensity, windIntensity)) {
                add(WeatherEchoKind.Clear)
            }
        }

        return WeatherEchoSnapshot(
            id = stableId(result.frames),
            kinds = kinds,
            rainIntensity = rainIntensity,
            snowIntensity = snowIntensity,
            windIntensity = windIntensity,
            primaryKind = primaryKind(kinds, rainIntensity, snowIntensity, windIntensity)
        )
    }

    private fun isClear(
        finalState: SimState,
        rainIntensity: Int,
        snowIntensity: Int,
        windIntensity: Int
    ): Boolean {
        if (rainIntensity > 0 || snowIntensity > 0 || windIntensity > 0) return false
        return finalState.cloud.all { it == 0 } && finalState.fog.all { it == 0 }
    }

    private fun primaryKind(
        kinds: List<WeatherEchoKind>,
        rainIntensity: Int,
        snowIntensity: Int,
        windIntensity: Int
    ): WeatherEchoKind? {
        if (kinds == listOf(WeatherEchoKind.Clear)) return WeatherEchoKind.Clear
        if (kinds.isEmpty()) return null

        var best: WeatherEchoKind? = null
        var bestIntensity = -1
        val candidates = listOf(
            WeatherEchoKind.Rain to rainIntensity,
            WeatherEchoKind.Snow to snowIntensity,
            WeatherEchoKind.Wind to windIntensity
        )
        for ((kind, intensity) in candidates) {
            if (intensity > bestIntensity && intensity > 0) {
                best = kind
                bestIntensity = intensity
            }
        }
        return best
    }

    /**
     * Stable run fingerprint. It intentionally ignores event text/order and wall-clock
     * state: only simulation version and the ordered deterministic frame fingerprints
     * contribute to the identifier.
     */
    private fun stableId(frames: List<SimState>): String {
        var fingerprint = 1125899906842597L
        fingerprint = fingerprint * 31 + SIM_VERSION
        fingerprint = fingerprint * 31 + frames.size
        for (frame in frames) {
            fingerprint = fingerprint * 31 + frame.hash()
        }
        val hex = java.lang.Long.toUnsignedString(fingerprint, 16).padStart(16, '0')
        return "echo-v$SIM_VERSION-$hex"
    }
}
