package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.InventoryEntry
import com.rork.weatherloom.core.terrarium.PlayerInventory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val CURRENT_SAVE_SCHEMA = 3

/**
 * Explicit, deterministic save decoding. Legacy saves are upgraded without changing
 * valid player progress/settings. Unknown fields remain forward-tolerant. Corrupt
 * payloads fall back to a fresh current-schema save.
 */
object SaveMigration {

    fun decode(raw: String?, json: Json): SaveData {
        if (raw.isNullOrBlank()) return SaveData()

        return runCatching {
            val element = json.parseToJsonElement(raw)
            val declaredSchema =
                element.jsonObject["schema"]?.jsonPrimitive?.intOrNull ?: 1
            val decoded = json.decodeFromJsonElement(SaveData.serializer(), element)

            when {
                declaredSchema <= 2 -> migrateLegacy(decoded)
                declaredSchema == CURRENT_SAVE_SCHEMA ->
                    canonicalizeKnown(decoded.copy(schema = CURRENT_SAVE_SCHEMA))
                else ->
                    // Preserve the future schema marker and every field this build
                    // understands. Do not canonicalize future-schema semantics that
                    // may intentionally extend ranges understood by this build.
                    decoded.copy(schema = declaredSchema)
            }
        }.getOrElse { SaveData() }
    }

    private fun migrateLegacy(legacy: SaveData): SaveData {
        val canonical = canonicalizeKnown(legacy.copy(schema = CURRENT_SAVE_SCHEMA))
        val existingKeys = canonical.terrariumInventory.entries.map { it.stableKey }.toSet()
        val migratedEntries = canonical.collectibles
            .filter { it.isNotBlank() }
            .map { id -> InventoryEntry(itemId = id, unlockSource = "legacy_collectible") }
            .filterNot { it.stableKey in existingKeys }

        return canonical.copy(
            terrariumInventory = PlayerInventory(
                canonical.terrariumInventory.entries + migratedEntries
            )
        )
    }

    private fun canonicalizeKnown(save: SaveData): SaveData =
        save.copy(
            levels = save.levels.mapValues { (_, record) ->
                record.copy(
                    rating = record.rating.coerceIn(0, Rating.entries.lastIndex),
                    attempts = record.attempts.coerceAtLeast(0),
                    bestStrokes = record.bestStrokes.coerceAtLeast(0),
                    bestCells = record.bestCells.coerceAtLeast(0)
                )
            },
            collectibles = save.collectibles.distinct(),
            dailyHistory = save.dailyHistory.distinct()
        )
}

/**
 * Serializes all read-modify-write save mutations behind one lock. This prevents
 * two concurrent callers from reading the same old snapshot and losing one update.
 * The persistence callback is invoked only when the resulting state actually changes.
 */
internal class SaveStateMutator(
    initial: SaveData,
    private val persist: (SaveData) -> Unit
) {
    @Volatile
    private var state: SaveData = initial

    fun snapshot(): SaveData = state

    fun mutate(transform: (SaveData) -> SaveData): SaveData = synchronized(this) {
        val next = transform(state)
        if (next != state) {
            state = next
            persist(next)
        }
        state
    }

    fun <T> mutateWithResult(transform: (SaveData) -> Pair<SaveData, T>): T = synchronized(this) {
        val (next, result) = transform(state)
        if (next != state) {
            state = next
            persist(next)
        }
        result
    }
}
