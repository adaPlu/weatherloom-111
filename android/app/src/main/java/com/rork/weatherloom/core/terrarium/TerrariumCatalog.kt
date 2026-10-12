package com.rork.weatherloom.core.terrarium

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned static content. Stable IDs are persistence contracts once shipped.
 * The catalog defines what can exist; it never records what a player owns.
 */
@Serializable
data class TerrariumCatalog(
    val schemaVersion: Int,
    val growthProfiles: List<GrowthProfile>,
    val items: List<TerrariumItem>
) {
    init {
        require(schemaVersion > 0) { "terrarium catalog schemaVersion must be positive" }

        val itemIds = items.map { it.id }
        require(itemIds.size == itemIds.distinct().size) { "terrarium item ids must be unique" }
        itemIds.forEach(::requireStableId)

        val profileIds = growthProfiles.map { it.id }
        require(profileIds.size == profileIds.distinct().size) { "growth profile ids must be unique" }
        profileIds.forEach(::requireStableId)

        val profiles = profileIds.toSet()
        items.forEach { item ->
            item.growthProfileId?.let { profileId ->
                require(profileId in profiles) {
                    "item ${item.id} references missing growth profile $profileId"
                }
            }
        }
    }

    fun item(id: String): TerrariumItem? = items.firstOrNull { it.id == id }

    fun growthProfile(id: String): GrowthProfile? = growthProfiles.firstOrNull { it.id == id }

    /** Structural validation that depends on static item metadata. */
    fun requireValid(layout: TerrariumLayout) {
        layout.placements.forEach { placement ->
            val item = item(placement.itemId)
                ?: throw IllegalArgumentException("placement references unknown item ${placement.itemId}")
            require(placement.rotation in item.allowedRotations) {
                "rotation ${placement.rotation} is not allowed for ${placement.itemId}"
            }
            val expected = item.footprint.rotated(placement.rotation)
            require(placement.logicalFootprint == expected) {
                "placement footprint ${placement.logicalFootprint} does not match $expected for ${placement.itemId}"
            }
        }
    }

    /** Growth progress is validated against its profile without coupling it to layout coordinates. */
    fun requireValid(growth: GrowthState) {
        val profile = growthProfile(growth.growthProfileId)
            ?: throw IllegalArgumentException("growth references unknown profile ${growth.growthProfileId}")
        require(growth.stageIndex in profile.stages.indices) {
            "growth stage ${growth.stageIndex} is outside profile ${profile.id}"
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val stableId = Regex("[a-z0-9][a-z0-9._-]*")

        fun decode(text: String): TerrariumCatalog =
            json.decodeFromString(serializer(), text)

        private fun requireStableId(id: String) {
            require(stableId.matches(id)) {
                "stable terrarium id '$id' must use lowercase letters, digits, dot, underscore, or dash"
            }
        }
    }
}
