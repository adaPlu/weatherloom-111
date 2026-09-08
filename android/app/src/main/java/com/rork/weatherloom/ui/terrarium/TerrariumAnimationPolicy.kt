package com.rork.weatherloom.ui.terrarium

/** Motion switches plus the equivalent static indicators required to communicate state. */
data class TerrariumRenderPolicy(
    val animateRain: Boolean,
    val animateSnow: Boolean,
    val animateWind: Boolean,
    val animateVegetation: Boolean,
    val animateVisitors: Boolean,
    val stateIndicators: List<String>
)

/** Deterministic animation policy. Motion may decorate state but never author it. */
object TerrariumAnimationPolicy {
    fun forSnapshot(
        snapshot: TerrariumVisualSnapshot,
        reducedMotion: Boolean
    ): TerrariumRenderPolicy {
        val motionEnabled = !reducedMotion
        return TerrariumRenderPolicy(
            animateRain = motionEnabled && snapshot.rainIntensity > 0,
            animateSnow = motionEnabled && snapshot.snowIntensity > 0,
            animateWind = motionEnabled && snapshot.windIntensity > 0,
            animateVegetation = motionEnabled && snapshot.specimens.isNotEmpty(),
            animateVisitors = motionEnabled && snapshot.visitors.isNotEmpty(),
            stateIndicators = snapshot.stateLabels
        )
    }
}
