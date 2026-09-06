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

    fun evaluateAndApply(save: SaveData): TerrariumReactionSaveResult {
        val environment = save.terrariumEnvironment
            ?: return TerrariumReactionSaveResult(
                save = save,
                reactions = ReactionResult(),
                newlyAppliedDurableEvents = emptyList()
            )

        val updatedGrowth = growthPulseService.apply(
            layout = save.terrariumLayout,
            current = save.terrariumGrowth,
            echo = environment.weatherEcho
        )
        val saveAfterGrowth = if (updatedGrowth == save.terrariumGrowth) {
            save
        } else {
            save.copy(terrariumGrowth = updatedGrowth)
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
}
