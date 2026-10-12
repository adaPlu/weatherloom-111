package com.rork.weatherloom.core.sim

/** The player's programmable input language: fronts woven across the sky. */
enum class ThreadType(val label: String, val shortLabel: String) {
    WarmFront("Warm Front", "Warm"),
    ColdFront("Cold Front", "Cold"),
    WindBand("Wind Band", "Wind"),
    MoistureRibbon("Moisture Ribbon", "Moisture");

    /** One-sentence rule shown in the thread inspector. */
    val rule: String
        get() = when (this) {
            WarmFront -> "Warms the air it crosses and carries a little humidity."
            ColdFront -> "Chills the air it crosses. Cold air cannot hold its moisture."
            WindBand -> "Pushes clouds and fog one tile each beat, and turns windmills."
            MoistureRibbon -> "Adds humidity. On its own it never rains."
        }
}

/**
 * A drawn stroke. [points] are normalised board coordinates (0..1) used for rendering,
 * [cells] are the grid indices the stroke resolved onto and drive the simulation.
 */
data class WeatherThread(
    val type: ThreadType,
    val points: List<Pair<Float, Float>>,
    val cells: List<Int>,
    val dir: Dir
)
