package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.InventoryEntry
import com.rork.weatherloom.core.terrarium.PlayerInventory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val CURRENT_SAVE_SCHEMA = 4

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
                declaredSchema <= 2 -> migratePreTerrarium(decoded)
                declaredSchema == 3 -> migratePlayerProgression(decoded)
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

    private fun migratePreTerrarium(legacy: SaveData): SaveData {
        val canonical = canonicalizeKnown(legacy.copy(schema = CURRENT_SAVE_SCHEMA))
        val existingKeys = canonical.terrariumInventory.entries.map { it.stableKey }.toSet()
        val migratedEntries = canonical.collectibles
            .filter { it.isNotBlank() }
            .map { id -> InventoryEntry(itemId = id, unlockSource = "legacy_collectible") }
            .filterNot { it.stableKey in existingKeys }

        return backfillPlayerProgression(
            canonical.copy(
                terrariumInventory = PlayerInventory(
                    canonical.terrariumInventory.entries + migratedEntries
                )
            )
        )
    }

    private fun migratePlayerProgression(schemaThree: SaveData): SaveData =
        backfillPlayerProgression(
            canonicalizeKnown(schemaThree.copy(schema = CURRENT_SAVE_SCHEMA))
        )

    private fun backfillPlayerProgression(save: SaveData): SaveData {
        val awarded = save.levels.mapNotNull { (levelId, record) ->
            val xp = PlayerXpRules.cumulativeXpFor(record.ratingEnum)
            if (levelId.isNotBlank() && xp > 0) levelId to xp else null
        }.toMap()

        return save.copy(
            playerProgression = PlayerProgression(
                xp = awarded.values.sum(),
                awardedLevelXp = awarded
            )
        )
    }

    private fun canonicalizeKnown(save: SaveData): SaveData {
        val levels = save.levels.mapValues { (_, record) ->
            record.copy(
                rating = record.rating.coerceIn(0, Rating.entries.lastIndex),
                attempts = record.attempts.coerceAtLeast(0),
                bestStrokes = record.bestStrokes.coerceAtLeast(0),
                bestCells = record.bestCells.coerceAtLeast(0)
            )
        }
        val awardedLevelXp = save.playerProgression.awardedLevelXp
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, xp) -> xp.coerceIn(0, PlayerXpRules.FLOURISH_XP) }
            .filterValues { it > 0 }
        val minimumXpFromLedger = awardedLevelXp.values.sum()

        return save.copy(
            levels = levels,
            collectibles = save.collectibles.distinct(),
            dailyHistory = save.dailyHistory.distinct(),
            playerProgression = PlayerProgression(
                xp = maxOf(save.playerProgression.xp.coerceAtLeast(0), minimumXpFromLedger),
                awardedLevelXp = awardedLevelXp
            )
        )
    }
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
