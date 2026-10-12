package com.rork.weatherloom.core.terrarium

import kotlinx.serialization.Serializable

@Serializable
enum class TerrariumCategory {
    Botanical,
    Fungus,
    Water,
    Structure,
    Mechanism,
    Decoration
}

@Serializable
enum class TerrariumRotation(val degrees: Int) {
    Deg0(0),
    Deg90(90),
    Deg180(180),
    Deg270(270)
}

@Serializable
data class TerrariumFootprint(
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0) { "footprint width must be positive" }
        require(height > 0) { "footprint height must be positive" }
    }

    fun rotated(rotation: TerrariumRotation): TerrariumFootprint = when (rotation) {
        TerrariumRotation.Deg0,
        TerrariumRotation.Deg180 -> this
        TerrariumRotation.Deg90,
        TerrariumRotation.Deg270 -> TerrariumFootprint(height, width)
    }
}

/**
 * Immutable content definition. This type intentionally contains no Android,
 * Compose, painter, resource, or persistence-runtime objects.
 */
@Serializable
data class TerrariumItem(
    val id: String,
    val nameKey: String,
    val category: TerrariumCategory,
    val visualFamily: String,
    val footprint: TerrariumFootprint,
    val allowedRotations: List<TerrariumRotation>,
    val reactionTags: List<String> = emptyList(),
    val growthProfileId: String? = null,
    val biomeTags: List<String> = emptyList(),
    val assetRef: String? = null
) {
    init {
        require(id.isNotBlank()) { "terrarium item id must not be blank" }
        require(nameKey.isNotBlank()) { "terrarium item nameKey must not be blank" }
        require(visualFamily.isNotBlank()) { "terrarium item visualFamily must not be blank" }
        require(allowedRotations.isNotEmpty()) { "terrarium item must allow at least one rotation" }
        require(allowedRotations.size == allowedRotations.distinct().size) {
            "terrarium item rotations must be unique"
        }
        require(reactionTags.all { it.isNotBlank() }) { "reaction tags must not be blank" }
        require(reactionTags.size == reactionTags.distinct().size) { "reaction tags must be unique" }
        require(biomeTags.all { it.isNotBlank() }) { "biome tags must not be blank" }
        require(biomeTags.size == biomeTags.distinct().size) { "biome tags must be unique" }
        growthProfileId?.let { require(it.isNotBlank()) { "growthProfileId must not be blank" } }
        assetRef?.let { require(it.isNotBlank()) { "assetRef must not be blank" } }
    }
}
