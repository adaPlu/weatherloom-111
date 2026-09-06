package com.rork.weatherloom.core.terrarium

import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot

/**
 * Pure, deterministic growth evaluator.
 *
 * Growth is driven by relevant Weather Echo semantics, never wall-clock time. Placement
 * coordinates are intentionally ignored; stable placement instance IDs carry biology.
 */
class GrowthPulseService(
    private val catalog: TerrariumCatalog
) {

    fun semantics(echo: WeatherEchoSnapshot): Set<String> = buildSet {
        echo.kinds.forEach { kind ->
            when (kind) {
                WeatherEchoKind.Rain -> {
                    add("rain")
                    add("moisture")
                }
                WeatherEchoKind.Snow -> {
                    add("snow")
                    add("cold")
                }
                WeatherEchoKind.Wind -> add("wind")
                WeatherEchoKind.Clear -> {
                    add("clear")
                    add("warm")
                }
            }
        }
    }

    fun apply(
        layout: TerrariumLayout,
        current: List<GrowthState>,
        echo: WeatherEchoSnapshot
    ): List<GrowthState> {
        val instanceIds = current.map { it.instanceId }
        require(instanceIds.size == instanceIds.distinct().size) {
            "growth states must have unique instanceIds"
        }

        val weatherSemantics = semantics(echo)
        val updated = current.associateBy { it.instanceId }.toMutableMap()

        layout.placements.sortedBy { it.instanceId }.forEach { placement ->
            val item = catalog.item(placement.itemId)
                ?: throw IllegalArgumentException("placement references unknown item ${placement.itemId}")
            val profileId = item.growthProfileId ?: return@forEach
            val profile = catalog.growthProfile(profileId)
                ?: throw IllegalArgumentException("missing growth profile $profileId")

            if (item.reactionTags.none { it in weatherSemantics }) return@forEach

            val existing = updated[placement.instanceId]
            if (existing != null) {
                require(existing.growthProfileId == profileId) {
                    "growth profile changed for instance ${placement.instanceId}"
                }
                catalog.requireValid(existing)
            }

            val consumedEchoIds = buildSet {
                existing?.appliedEchoIds?.let(::addAll)
                existing?.lastRelevantEchoId?.let(::add)
            }
            if (echo.id in consumedEchoIds) return@forEach

            val base = existing ?: GrowthState(
                instanceId = placement.instanceId,
                growthProfileId = profileId
            )
            val nextPulseCount = base.growthPulsesApplied + 1
            val stageFromPulses = minOf(
                nextPulseCount / profile.pulsesPerStage,
                profile.stages.lastIndex
            )
            val nextStageIndex = maxOf(base.stageIndex, stageFromPulses)
            updated[placement.instanceId] = base.copy(
                stageIndex = nextStageIndex,
                growthPulsesApplied = nextPulseCount,
                lastRelevantEchoId = echo.id,
                appliedEchoIds = (base.appliedEchoIds + echo.id).distinct()
            )
        }

        return updated.values.sortedBy { it.instanceId }
    }
}
