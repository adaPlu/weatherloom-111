package com.rork.weatherloom.core.terrarium

import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.weather.WeatherEchoKind

/** Result of applying one serialized Weather Echo to Terrarium growth. */
data class GrowthPulseResult(
    val growthStates: List<GrowthState>,
    val pulsedInstanceIds: List<String>
)

/**
 * Pure deterministic growth evaluator.
 *
 * Growth is driven only by relevant Weather Echo semantics. Placement coordinates and
 * wall-clock time are deliberately irrelevant; durable identity is the placement instance ID.
 */
object GrowthPulseService {

    fun apply(
        layout: TerrariumLayout,
        growthStates: List<GrowthState>,
        environment: EnvironmentState,
        catalog: TerrariumCatalog
    ): GrowthPulseResult {
        val growthInstanceIds = growthStates.map { it.instanceId }
        require(growthInstanceIds.distinct().size == growthInstanceIds.size) {
            "growth states must contain unique instance ids"
        }
        growthStates.forEach(catalog::requireValid)

        val statesByInstance = growthStates.associateBy { it.instanceId }.toMutableMap()
        val pulsedInstanceIds = mutableListOf<String>()
        val semanticTags = weatherSemanticTags(environment)
        val echoId = environment.weatherEcho.id

        for (placement in layout.placements.sortedBy { it.instanceId }) {
            val item = catalog.item(placement.itemId) ?: continue
            val profileId = item.growthProfileId ?: continue
            if (item.reactionTags.none { it in semanticTags }) continue

            val profile = catalog.growthProfile(profileId)
                ?: throw IllegalArgumentException("item ${item.id} references missing growth profile $profileId")
            val existing = statesByInstance[placement.instanceId]

            if (existing != null) {
                require(existing.growthProfileId == profileId) {
                    "growth profile ${existing.growthProfileId} does not match $profileId for ${placement.instanceId}"
                }
                if (existing.lastRelevantEchoId == echoId) continue
            }

            val current = existing ?: GrowthState(
                instanceId = placement.instanceId,
                growthProfileId = profileId
            )
            val maxStageIndex = profile.stages.lastIndex
            val maxGrowthPulses = maxStageIndex * profile.pulsesPerStage

            if (current.stageIndex >= maxStageIndex || current.growthPulsesApplied >= maxGrowthPulses) {
                statesByInstance[placement.instanceId] = current.copy(
                    stageIndex = maxStageIndex,
                    growthPulsesApplied = maxGrowthPulses,
                    lastRelevantEchoId = echoId
                )
                continue
            }

            val nextPulses = (current.growthPulsesApplied + 1).coerceAtMost(maxGrowthPulses)
            val nextStageIndex = (nextPulses / profile.pulsesPerStage).coerceAtMost(maxStageIndex)
            statesByInstance[placement.instanceId] = current.copy(
                stageIndex = maxOf(current.stageIndex, nextStageIndex),
                growthPulsesApplied = nextPulses,
                lastRelevantEchoId = echoId
            )
            pulsedInstanceIds += placement.instanceId
        }

        return GrowthPulseResult(
            growthStates = statesByInstance.values.sortedBy { it.instanceId },
            pulsedInstanceIds = pulsedInstanceIds.sorted()
        )
    }

    private fun weatherSemanticTags(environment: EnvironmentState): Set<String> {
        val tags = mutableSetOf<String>()
        for (kind in environment.weatherEcho.kinds) {
            when (kind) {
                WeatherEchoKind.Rain -> {
                    tags += "rain"
                    tags += "moisture"
                }
                WeatherEchoKind.Snow -> {
                    tags += "snow"
                    tags += "cold"
                }
                WeatherEchoKind.Wind -> tags += "wind"
                WeatherEchoKind.Clear -> {
                    tags += "clear"
                    tags += "warm"
                }
            }
        }
        return tags
    }
}
