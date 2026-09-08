package com.rork.weatherloom.ui.almanac

import com.rork.weatherloom.core.level.Collectible
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot

/** Pure, deterministic projection from authored/static state into player-facing Almanac rows. */
object AlmanacProjection {

    private data class WeatherNote(
        val id: String,
        val kind: WeatherEchoKind,
        val title: String,
        val body: String
    )

    private data class DiscoveryNote(
        val id: String,
        val sourceItemId: String,
        val title: String,
        val body: String,
        val clue: String,
        val order: Int
    )

    private val weatherNotes = listOf(
        WeatherNote(
            id = "weather.rain",
            kind = WeatherEchoKind.Rain,
            title = "Rain",
            body = "Falling water gathers when a cloud becomes heavy enough to let its moisture go."
        ),
        WeatherNote(
            id = "weather.snow",
            kind = WeatherEchoKind.Snow,
            title = "Snow",
            body = "Water falls as snow when the air along its path is cold enough to freeze it."
        ),
        WeatherNote(
            id = "weather.wind",
            kind = WeatherEchoKind.Wind,
            title = "Wind",
            body = "Moving air pushes clouds and fog, while stronger wind can clear fog away."
        ),
        WeatherNote(
            id = "weather.clear",
            kind = WeatherEchoKind.Clear,
            title = "Clear skies",
            body = "Clear conditions mean rain, snow, and wind are absent from the current echo."
        )
    )

    private val discoveryNotes = listOf(
        DiscoveryNote(
            id = "rainbell_after_rain",
            sourceItemId = "rainbell",
            title = "Rainbell After Rain",
            body = "After rain, a Rainbell can glisten, bloom, and welcome a butterfly visitor.",
            clue = "Let a Rainbell experience rain and watch closely for a visitor.",
            order = 0
        )
    )

    fun project(
        collectibles: List<Collectible>,
        speciesDiscovered: Set<String>,
        terrariumCatalog: TerrariumCatalog,
        weatherEcho: WeatherEchoSnapshot?,
        terrariumDiscoveries: Set<String>
    ): AlmanacContent = AlmanacContent(
        species = species(collectibles, speciesDiscovered),
        weather = weather(weatherEcho),
        discoveries = discoveries(terrariumDiscoveries, terrariumCatalog)
    )

    fun species(
        collectibles: List<Collectible>,
        discovered: Set<String>
    ): List<AlmanacEntryViewData> = collectibles
        .sortedBy { it.id }
        .map { it.toAlmanacEntry(discovered) }

    fun weather(): List<AlmanacEntryViewData> = weather(null)

    fun weather(snapshot: WeatherEchoSnapshot?): List<AlmanacEntryViewData> = weatherNotes.map { note ->
        AlmanacEntryViewData(
            id = note.id,
            title = note.title,
            body = note.body,
            label = if (snapshot?.kinds?.contains(note.kind) == true) "Current echo" else "Weather",
            clue = null,
            discovered = true
        )
    }

    fun discoveries(discovered: Set<String>): List<AlmanacEntryViewData> =
        discoveries(discovered, null)

    fun discoveries(
        discovered: Set<String>,
        catalog: TerrariumCatalog?
    ): List<AlmanacEntryViewData> = discoveryNotes
        .asSequence()
        .filter { note -> catalog == null || catalog.item(note.sourceItemId) != null }
        .sortedBy { it.order }
        .map { note ->
            val found = note.id in discovered
            if (found) {
                AlmanacEntryViewData(
                    id = note.id,
                    title = note.title,
                    body = note.body,
                    label = "Discovery",
                    clue = null,
                    discovered = true
                )
            } else {
                AlmanacEntryViewData(
                    id = note.id,
                    title = "Undiscovered",
                    body = "A Terrarium secret is waiting to be noticed.",
                    label = "Discovery",
                    clue = note.clue,
                    discovered = false
                )
            }
        }
        .toList()
}
