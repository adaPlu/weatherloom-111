package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.GrowthPulseService
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.reaction.DurableReactionEvent
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
 * Pure bridge between durable [SaveData] and deterministic Terrarium growth/reactions.
 *
 * Growth and durable reaction event IDs are applied to one returned save snapshot so the
 * repository's serialized mutation gate persists them atomically. Visual reaction state is
 * recomputable and is never persisted.
 */
class TerrariumReactionSaveService(
    private val catalog: TerrariumCatalog,
    private val reactions: ReactionCatalog
) {
    fun evaluateAndApply(save: SaveData): TerrariumReactionSaveResult {
        val environment = save.terrariumEnvironment
            ?: return TerrariumReactionSaveResult(
                save = save,
                reactions = ReactionResult(),
                newlyAppliedDurableEvents = emptyList()
            )

        val growthResult = GrowthPulseService.apply(
            layout = save.terrariumLayout,
            growthStates = save.terrariumGrowth,
            environment = environment,
            catalog = catalog
        )
        val saveAfterGrowth = if (growthResult.growthStates == save.terrariumGrowth) {
            save
        } else {
            save.copy(terrariumGrowth = growthResult.growthStates)
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
        if (newlyApplied.isEmpty()) {
            return TerrariumReactionSaveResult(
                save = saveAfterGrowth,
                reactions = reactionResult,
                newlyAppliedDurableEvents = emptyList()
            )
        }

        val appliedIds = (
            saveAfterGrowth.appliedTerrariumReactionEventIds + newlyApplied.map { it.id }
        ).distinct().sorted()

        return TerrariumReactionSaveResult(
            save = saveAfterGrowth.copy(appliedTerrariumReactionEventIds = appliedIds),
            reactions = reactionResult,
            newlyAppliedDurableEvents = newlyApplied
        )
    }
}
