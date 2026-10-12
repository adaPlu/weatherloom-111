package com.rork.weatherloom.ui.almanac

import com.rork.weatherloom.core.level.Collectible

data class AlmanacEntryViewData(
    val id: String,
    val title: String,
    val body: String,
    val label: String?,
    val clue: String?,
    val discovered: Boolean
)

data class AlmanacContent(
    val species: List<AlmanacEntryViewData>,
    val weather: List<AlmanacEntryViewData>,
    val discoveries: List<AlmanacEntryViewData>
)

internal fun Collectible.toAlmanacEntry(discoveredIds: Set<String>): AlmanacEntryViewData {
    val found = id in discoveredIds
    return if (found) {
        AlmanacEntryViewData(
            id = id,
            title = name,
            body = flavour,
            label = biome,
            clue = null,
            discovered = true
        )
    } else {
        AlmanacEntryViewData(
            id = id,
            title = "Undiscovered",
            body = unlock,
            label = "Species",
            clue = null,
            discovered = false
        )
    }
}
