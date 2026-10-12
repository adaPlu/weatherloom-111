package com.rork.weatherloom.core.terrarium.reaction

import com.rork.weatherloom.core.terrarium.GrowthState
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumLayout

/**
 * Pure deterministic evaluator from Terrarium domain snapshots to recomputable visual
 * and visitor state plus idempotent durable event candidates. It performs no persistence itself.
 */
object ReactionEngine {

    fun evaluate(
        layout: TerrariumLayout,
        growthStates: List<GrowthState>,
        environment: EnvironmentState,
        catalog: TerrariumCatalog,
        reactions: ReactionCatalog,
        appliedDurableEventIds: Set<String>
    ): ReactionResult {
        val growthInstanceIds = growthStates.map { it.instanceId }
        require(growthInstanceIds.distinct().size == growthInstanceIds.size) {
            "growth states must contain unique instance ids"
        }

        val visualTagsByInstance = mutableMapOf<String, MutableSet<String>>()
        val visitorsById = mutableMapOf<String, VisitorPresence>()
        val durableEventsById = mutableMapOf<String, DurableReactionEvent>()
        val orderedRules = reactions.rules.sortedBy { it.id }

        for (placement in layout.placements.sortedBy { it.instanceId }) {
            val item = catalog.item(placement.itemId) ?: continue

            for (rule in orderedRules) {
                if (!matches(rule, item, environment)) continue

                if (rule.result.visualTags.isNotEmpty()) {
                    visualTagsByInstance
                        .getOrPut(placement.instanceId) { mutableSetOf() }
                        .addAll(rule.result.visualTags)
                }

                for (visitorId in rule.result.visitorIds) {
                    visitorsById.putIfAbsent(
                        visitorId,
                        VisitorPresence(
                            visitorId = visitorId,
                            ruleId = rule.id,
                            sourceInstanceId = placement.instanceId
                        )
                    )
                }

                for (definition in rule.result.durableEvents) {
                    if (definition.id in appliedDurableEventIds) continue
                    durableEventsById.putIfAbsent(
                        definition.id,
                        DurableReactionEvent(
                            id = definition.id,
                            kind = definition.kind,
                            payloadId = definition.payloadId,
                            ruleId = rule.id,
                            sourceInstanceId = placement.instanceId
                        )
                    )
                }
            }
        }

        val visualStates = visualTagsByInstance.keys.sorted().map { instanceId ->
            ReactionVisualState(
                instanceId = instanceId,
                visualTags = visualTagsByInstance.getValue(instanceId).sorted()
            )
        }
        val visitors = visitorsById.keys.sorted().map(visitorsById::getValue)
        val durableEvents = durableEventsById.keys.sorted().map(durableEventsById::getValue)

        return ReactionResult(
            visualStates = visualStates,
            pendingDurableEvents = durableEvents,
            visitors = visitors
        )
    }

    private fun matches(
        rule: ReactionRule,
        item: TerrariumItem,
        environment: EnvironmentState
    ): Boolean =
        rule.requires.itemTags.all { it in item.reactionTags } &&
            rule.requires.weatherKinds.all { it in environment.weatherEcho.kinds } &&
            rule.requires.environmentModifiers.all { it in environment.modifiers }
}
