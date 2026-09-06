package com.rork.weatherloom.data

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
 * Pure bridge between durable [SaveData] and the deterministic [ReactionEngine].
 *
 * Visual reaction state is recomputable and is never persisted. Durable event IDs are
 * appended exactly once in canonical order so restarts/re-evaluation cannot duplicate a
 * discovery candidate or future durable reaction side effect.
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

        val reactionResult = ReactionEngine.evaluate(
            layout = save.terrariumLayout,
            growthStates = save.terrariumGrowth,
            environment = environment,
            catalog = catalog,
            reactions = reactions,
            appliedDurableEventIds = save.appliedTerrariumReactionEventIds.toSet()
        )

        val newlyApplied = reactionResult.pendingDurableEvents
        if (newlyApplied.isEmpty()) {
            return TerrariumReactionSaveResult(
                save = save,
                reactions = reactionResult,
                newlyAppliedDurableEvents = emptyList()
            )
        }

        val appliedIds = (
            save.appliedTerrariumReactionEventIds + newlyApplied.map { it.id }
        ).distinct().sorted()

        return TerrariumReactionSaveResult(
            save = save.copy(appliedTerrariumReactionEventIds = appliedIds),
            reactions = reactionResult,
            newlyAppliedDurableEvents = newlyApplied
        )
    }
}
