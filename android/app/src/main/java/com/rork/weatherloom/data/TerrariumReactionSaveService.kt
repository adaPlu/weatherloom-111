package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.GrowthPulseService
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.reaction.DurableReactionEvent
import com.rork.weatherloom.core.terrarium.reaction.DurableReactionEventKind
import com.rork.weatherloom.core.terrarium.reaction.ReactionCatalog
import com.rork.weatherloom.core.terrarium.reaction.ReactionEngine
import com.rork.weatherloom.core.terrarium.reaction.ReactionResult

/** Durable application result for one deterministic Terrarium reaction evaluation. */
data class TerrariumReactionSaveResult(
    val save: SaveData,
    val reactions: ReactionResult,
    val newlyAppliedDurableEvents: List<DurableReactionEvent>
)

/**
 * Pure bridge between durable [SaveData] and the deterministic Terrarium evaluators.
 *
 * A relevant persisted Weather Echo first produces deterministic Growth Pulses, then the
 * ReactionEngine evaluates the same updated growth snapshot. Growth, discoveries, and
 * durable reaction IDs are returned as one [SaveData] value so the repository can commit
 * them through its single serialized mutation gate.
 *
 * Visual and visitor reaction state is recomputable and is never persisted. Durable event
 * IDs and discovery payload IDs are appended exactly once in canonical order so restarts
 * and re-evaluation cannot duplicate a discovery or future durable reaction side effect.
 */
class TerrariumReactionSaveService(
    private val catalog: TerrariumCatalog,
    private val reactions: ReactionCatalog
) {
    private val growthPulseService = GrowthPulseService(catalog)
    private val discoveryPayloadByEventId = reactions.rules
        .flatMap { it.result.durableEvents }
        .filter { it.kind == DurableReactionEventKind.DiscoveryCandidate }
        .associate { it.id to it.payloadId }

    fun evaluateAndApply(save: SaveData): TerrariumReactionSaveResult {
        val saveWithDiscoveryBackfill = backfillKnownDiscoveries(save)
        val environment = saveWithDiscoveryBackfill.terrariumEnvironment
            ?: return TerrariumReactionSaveResult(
                save = saveWithDiscoveryBackfill,
                reactions = ReactionResult(),
                newlyAppliedDurableEvents = emptyList()
            )

        val updatedGrowth = growthPulseService.apply(
            layout = saveWithDiscoveryBackfill.terrariumLayout,
            current = saveWithDiscoveryBackfill.terrariumGrowth,
            echo = environment.weatherEcho
        )
        val saveAfterGrowth = if (updatedGrowth == saveWithDiscoveryBackfill.terrariumGrowth) {
            saveWithDiscoveryBackfill
        } else {
            saveWithDiscoveryBackfill.copy(terrariumGrowth = updatedGrowth)
        }

        val reactionResult = ReactionEngine.evaluate(
            layout = saveAfterGrowth.terrariumLayout,
            growthStates = saveAfterGrowth.terrariumGrowth,
            environment = environment,
            catalog = catalog,
            reactions = reactions,
            appliedDurableEventIds = saveAfterGrowth.appliedTerrariumReactionEventIds.toSet()
        )

        val newlyApplied = reactionResult.pendingDurableEvents
        val discoveredPayloadIds = newlyApplied
            .filter { it.kind == DurableReactionEventKind.DiscoveryCandidate }
            .map { it.payloadId }
        val discoveries = (
            saveAfterGrowth.terrariumDiscoveries + discoveredPayloadIds
        ).distinct().sorted()
        val saveAfterDiscoveries = if (discoveries == saveAfterGrowth.terrariumDiscoveries) {
            saveAfterGrowth
        } else {
            saveAfterGrowth.copy(terrariumDiscoveries = discoveries)
        }

        if (newlyApplied.isEmpty()) {
            return TerrariumReactionSaveResult(
                save = saveAfterDiscoveries,
                reactions = reactionResult,
                newlyAppliedDurableEvents = emptyList()
            )
        }

        val appliedIds = (
            saveAfterDiscoveries.appliedTerrariumReactionEventIds + newlyApplied.map { it.id }
        ).distinct().sorted()

        return TerrariumReactionSaveResult(
            save = saveAfterDiscoveries.copy(appliedTerrariumReactionEventIds = appliedIds),
            reactions = reactionResult,
            newlyAppliedDurableEvents = newlyApplied
        )
    }

    private fun backfillKnownDiscoveries(save: SaveData): SaveData {
        val discoveriesFromAppliedEvents = save.appliedTerrariumReactionEventIds
            .mapNotNull(discoveryPayloadByEventId::get)
        val discoveries = (
            save.terrariumDiscoveries + discoveriesFromAppliedEvents
        ).distinct().sorted()

        return if (discoveries == save.terrariumDiscoveries) {
            save
        } else {
            save.copy(terrariumDiscoveries = discoveries)
        }
    }
}
