package com.rork.weatherloom.core.terrarium.reaction

import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import kotlinx.serialization.Serializable

/**
 * Stable Terrarium environmental input. This is deliberately a compact snapshot,
 * not a continuously simulated copy of puzzle SimState.
 */
@Serializable
data class EnvironmentState(
    val weatherEcho: WeatherEchoSnapshot,
    val modifiers: List<String> = emptyList()
) {
    init {
        require(modifiers.distinct().size == modifiers.size) {
            "environment modifiers must be unique"
        }
        require(modifiers.all(::isStableReactionId)) {
            "environment modifiers must use stable lowercase ids"
        }
        require(modifiers == modifiers.sorted()) {
            "environment modifiers must use canonical ordering"
        }
    }
}

internal val REACTION_STABLE_ID = Regex("[a-z0-9][a-z0-9._-]*")

internal fun isStableReactionId(value: String): Boolean =
    value.isNotBlank() && REACTION_STABLE_ID.matches(value)

internal fun requireStableReactionId(value: String, label: String) {
    require(isStableReactionId(value)) {
        "$label '$value' must use lowercase letters, digits, dot, underscore, or dash"
    }
}
