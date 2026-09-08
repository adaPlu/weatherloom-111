package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.InventoryEntry
import com.rork.weatherloom.core.terrarium.PlayerInventory
import com.rork.weatherloom.core.terrarium.reaction.isStableReactionId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val CURRENT_SAVE_SCHEMA = 6

internal data class SaveLoadResult(
    val save: SaveData,
    val writable: Boolean = save.schema <= CURRENT_SAVE_SCHEMA,
    val recoveryRequired: Boolean = false,
    val recoveryRaw: String? = null
)

/**
 * Explicit, deterministic save decoding. Legacy saves are upgraded without changing
 * valid player progress/settings. Unknown fields remain forward-tolerant. Malformed
 * payloads are quarantined by [load] so the original raw data cannot be silently lost.
 */
object SaveMigration {

    internal fun load(raw: String?, json: Json): SaveLoadResult {
        if (raw.isNullOrBlank()) return SaveLoadResult(SaveData())

        return runCatching { decodeParsed(raw, json) }
            .fold(
                onSuccess = { save ->
                    SaveLoadResult(
                        save = save,
                        writable = save.schema <= CURRENT_SAVE_SCHEMA
                    )
                },
                onFailure = {
                    SaveLoadResult(
                        save = SaveData(),
                        writable = false,
                        recoveryRequired = true,
                        recoveryRaw = raw
                    )
                }
            )
    }

    fun decode(raw: String?, json: Json): SaveData = load(raw, json).save

    private fun decodeParsed(raw: String, json: Json): SaveData {
        val element = json.parseToJsonElement(raw)
        val declaredSchema =
            element.jsonObject["schema"]?.jsonPrimitive?.intOrNull ?: 1
        val decoded = json.decodeFromJsonElement(SaveData.serializer(), element)

        return when {
            declaredSchema <= 2 -> migratePreTerrarium(decoded)
            declaredSchema == 3 -> migratePlayerProgression(decoded)
            declaredSchema == 4 -> migrateTerrariumReactionState(decoded)
            declaredSchema == 5 -> migrateVisitorDiscoveryState(decoded)
            declaredSchema == CURRENT_SAVE_SCHEMA ->
                canonicalizeKnown(decoded.copy(schema = CURRENT_SAVE_SCHEMA))
            else ->
                // Preserve the future schema marker and every field this build
                // understands. Do not canonicalize future-schema semantics that
                // may intentionally extend ranges understood by this build.
                decoded.copy(schema = declaredSchema)
        }
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

    /** Schema 4 predates persisted Terrarium environment/durable reaction event state. */
    private fun migrateTerrariumReactionState(schemaFour: SaveData): SaveData =
        canonicalizeKnown(schemaFour.copy(schema = CURRENT_SAVE_SCHEMA))

    /** Schema 5 predates the durable discovery registry introduced with visitors. */
    private fun migrateVisitorDiscoveryState(schemaFive: SaveData): SaveData =
        canonicalizeKnown(schemaFive.copy(schema = CURRENT_SAVE_SCHEMA))

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
        val reactionEventIds = save.appliedTerrariumReactionEventIds
            .filter(::isStableReactionId)
            .distinct()
            .sorted()
        val discoveries = save.terrariumDiscoveries
            .filter(::isStableReactionId)
            .distinct()
            .sorted()

        return save.copy(
            levels = levels,
            collectibles = save.collectibles.distinct(),
            dailyHistory = save.dailyHistory.distinct(),
            playerProgression = PlayerProgression(
                xp = maxOf(save.playerProgression.xp.coerceAtLeast(0), minimumXpFromLedger),
                awardedLevelXp = awardedLevelXp
            ),
            appliedTerrariumReactionEventIds = reactionEventIds,
            terrariumDiscoveries = discoveries
        )
    }
}

/**
 * Serializes all read-modify-write save mutations behind one lock. This prevents
 * two concurrent callers from reading the same old snapshot and losing one update.
 * Changed state becomes authoritative only after the persistence callback succeeds.
 *
 * Saves written by a newer schema or quarantined after corrupt decoding remain read-only
 * until an explicit [recover] action durably replaces them with a current-schema save.
 */
internal class SaveStateMutator(
    initial: SaveData,
    writable: Boolean = initial.schema <= CURRENT_SAVE_SCHEMA,
    private val persist: (SaveData) -> Unit
) {
    @Volatile
    private var state: SaveData = initial
    private var writable = writable

    fun snapshot(): SaveData = state

    fun mutate(transform: (SaveData) -> SaveData): SaveData = synchronized(this) {
        if (!writable) return@synchronized state

        val current = state
        val next = transform(current)
        if (next == current) return@synchronized current

        if (runCatching { persist(next) }.isSuccess) {
            state = next
        }
        state
    }

    fun <T : Any> mutateWithResult(
        transform: (SaveData) -> Pair<SaveData, T>
    ): T? = synchronized(this) {
        val current = state
        val (next, result) = transform(current)
        if (next == current) return@synchronized result
        if (!writable) return@synchronized null

        if (runCatching { persist(next) }.isFailure) {
            return@synchronized null
        }
        state = next
        result
    }

    fun recover(replacement: SaveData): SaveData = synchronized(this) {
        require(replacement.schema <= CURRENT_SAVE_SCHEMA) {
            "Recovery replacement must be understood by this save schema"
        }
        if (runCatching { persist(replacement) }.isFailure) {
            return@synchronized state
        }
        state = replacement
        writable = true
        state
    }
}
