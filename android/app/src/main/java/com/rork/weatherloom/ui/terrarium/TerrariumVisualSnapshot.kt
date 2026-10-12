package com.rork.weatherloom.ui.terrarium

import com.rork.weatherloom.core.terrarium.GrowthStage
import com.rork.weatherloom.core.terrarium.GrowthState
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumLayout
import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.terrarium.reaction.ReactionResult

/** Immutable, deterministic read model for the living Terrarium presentation. */
data class TerrariumSpecimenVisual(
    val instanceId: String,
    val itemId: String,
    val displayName: String,
    val growthStage: GrowthStage,
    val visualTags: List<String>
)

data class TerrariumVisitorVisual(
    val visitorId: String,
    val label: String,
    val sourceItemId: String?
)

data class TerrariumVisualSnapshot(
    val visibleItemIds: List<String>,
    val specimens: List<TerrariumSpecimenVisual>,
    val visitors: List<TerrariumVisitorVisual>,
    val rainIntensity: Int,
    val snowIntensity: Int,
    val windIntensity: Int,
    val stateLabels: List<String>
)

/**
 * One-way projection from existing authoritative snapshots into player-facing render state.
 * It performs no persistence, simulation, scheduling, clock reads, or random selection.
 */
object TerrariumVisualProjection {
    fun project(
        unlockedItemIds: List<String>,
        layout: TerrariumLayout,
        growthStates: List<GrowthState>,
        environment: EnvironmentState?,
        reactions: ReactionResult,
        catalog: TerrariumCatalog
    ): TerrariumVisualSnapshot {
        val visibleItemIds = unlockedItemIds
            .distinct()
            .filter { catalog.item(it) != null }
            .sorted()
        val growthByInstance = growthStates.associateBy { it.instanceId }
        val visualTagsByInstance = reactions.visualStates.associate { visual ->
            visual.instanceId to visual.visualTags.distinct().sorted()
        }

        val specimens = layout.placements
            .asSequence()
            .filter { it.itemId in visibleItemIds }
            .mapNotNull { placement ->
                val item = catalog.item(placement.itemId) ?: return@mapNotNull null
                val growth = growthByInstance[placement.instanceId]
                val profile = growth?.let { catalog.growthProfile(it.growthProfileId) }
                    ?: item.growthProfileId?.let(catalog::growthProfile)
                val stage = growth?.stageIndex?.let { profile?.stages?.getOrNull(it) }
                    ?: profile?.stages?.firstOrNull()
                    ?: GrowthStage.Seed
                TerrariumSpecimenVisual(
                    instanceId = placement.instanceId,
                    itemId = placement.itemId,
                    displayName = item.nameKey,
                    growthStage = stage,
                    visualTags = visualTagsByInstance[placement.instanceId].orEmpty()
                )
            }
            .sortedWith(compareBy<TerrariumSpecimenVisual>({ it.itemId }, { it.instanceId }))
            .toList()

        val itemByInstance = layout.placements.associate { it.instanceId to it.itemId }
        val visitors = reactions.visitors
            .map { visitor ->
                TerrariumVisitorVisual(
                    visitorId = visitor.visitorId,
                    label = visitorLabel(visitor.visitorId),
                    sourceItemId = itemByInstance[visitor.sourceInstanceId]
                )
            }
            .sortedBy { it.visitorId }

        val weather = environment?.weatherEcho
        val rainIntensity = weather?.rainIntensity ?: 0
        val snowIntensity = weather?.snowIntensity ?: 0
        val windIntensity = weather?.windIntensity ?: 0

        val stateLabels = buildList {
            if (rainIntensity > 0) add("Rain")
            if (snowIntensity > 0) add("Snow")
            if (windIntensity > 0) add("Wind")
            specimens.forEach { specimen ->
                specimenStateLabel(specimen)?.let(::add)
            }
            visitors.mapTo(this) { it.label }
        }

        return TerrariumVisualSnapshot(
            visibleItemIds = visibleItemIds,
            specimens = specimens,
            visitors = visitors,
            rainIntensity = rainIntensity,
            snowIntensity = snowIntensity,
            windIntensity = windIntensity,
            stateLabels = stateLabels
        )
    }

    private fun specimenStateLabel(specimen: TerrariumSpecimenVisual): String? {
        val state = when {
            "bloom" in specimen.visualTags || specimen.growthStage == GrowthStage.Bloom -> "in bloom"
            "wet" in specimen.visualTags -> "rain-kissed"
            specimen.growthStage == GrowthStage.Mature -> "mature"
            specimen.growthStage == GrowthStage.Young -> "growing"
            specimen.growthStage == GrowthStage.Sprout -> "sprouting"
            else -> null
        }
        return state?.let { "${specimen.displayName} $it" }
    }

    private fun visitorLabel(visitorId: String): String = when (visitorId) {
        "butterfly" -> "Butterfly visitor"
        else -> "Visitor present"
    }
}
