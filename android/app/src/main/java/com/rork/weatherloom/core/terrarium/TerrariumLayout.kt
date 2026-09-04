package com.rork.weatherloom.core.terrarium

import kotlinx.serialization.Serializable

@Serializable
data class TerrariumPlacement(
    val instanceId: String,
    val itemId: String,
    val variantId: String = "default",
    val xNormalized: Float,
    val yNormalized: Float,
    val rotation: TerrariumRotation = TerrariumRotation.Deg0,
    val logicalFootprint: TerrariumFootprint,
    val depthLayer: Int = 0
) {
    init {
        require(instanceId.isNotBlank()) { "placement instanceId must not be blank" }
        require(itemId.isNotBlank()) { "placement itemId must not be blank" }
        require(variantId.isNotBlank()) { "placement variantId must not be blank" }
        require(xNormalized.isFinite() && xNormalized in 0f..1f) {
            "xNormalized must be finite and within 0..1"
        }
        require(yNormalized.isFinite() && yNormalized in 0f..1f) {
            "yNormalized must be finite and within 0..1"
        }
        require(depthLayer >= 0) { "depthLayer must not be negative" }
    }
}

/** Placement only. Growth/biology is intentionally not represented here. */
@Serializable
data class TerrariumLayout(
    val placements: List<TerrariumPlacement> = emptyList()
) {
    init {
        val instanceIds = placements.map { it.instanceId }
        require(instanceIds.size == instanceIds.distinct().size) {
            "terrarium placement instanceIds must be unique"
        }
    }

    fun instance(instanceId: String): TerrariumPlacement? =
        placements.firstOrNull { it.instanceId == instanceId }
}
