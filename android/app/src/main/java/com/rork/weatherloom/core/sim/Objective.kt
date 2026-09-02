package com.rork.weatherloom.core.sim

import com.rork.weatherloom.core.level.Level

/** Data-driven objective metrics. Levels compose these; no level-specific gameplay code. */
enum class Metric {
    ReservoirWater,
    BloomedFlowers,
    FrozenCrops,
    SnowTiles,
    VillageFog,
    WindmillTicks,
    FloodedTiles,
    WetlandWater
}

enum class Cmp { Eq, Gte, Lte }

data class ObjectiveSpec(
    val metric: Metric,
    val cmp: Cmp,
    val target: Int,
    val label: String,
    /** Windmill objectives count ticks per windmill; this requires every windmill to reach target. */
    val everyWindmill: Boolean = false
)

data class ObjectiveProgress(
    val spec: ObjectiveSpec,
    val current: Int,
    val met: Boolean
) {
    val display: String
        get() = when (spec.cmp) {
            Cmp.Eq, Cmp.Gte -> "$current/${spec.target}"
            Cmp.Lte -> if (spec.target == 0) (if (current == 0) "safe" else "$current") else "$current/${spec.target}"
        }
}

object Objectives {

    fun measure(spec: ObjectiveSpec, level: Level, state: SimState): Int {
        val cells = level.cells
        return when (spec.metric) {
            Metric.ReservoirWater -> cells.indices
                .filter { cells[it].terrain == TerrainType.Reservoir }
                .sumOf { state.storage[it] }

            Metric.BloomedFlowers -> cells.indices
                .count { cells[it].feature == Feature.Flower && state.bloom[it] >= 2 }

            Metric.FrozenCrops -> cells.indices
                .count { cells[it].terrain == TerrainType.Crop && state.frozen[it] == 1 }

            Metric.SnowTiles -> cells.indices
                .count { cells[it].terrain == TerrainType.Mountain && state.snow[it] >= 1 }

            Metric.VillageFog -> cells.indices
                .filter { cells[it].terrain == TerrainType.Village }
                .sumOf { state.fog[it] }

            Metric.WindmillTicks -> {
                val mills = cells.indices.filter { cells[it].feature == Feature.Windmill }
                if (mills.isEmpty()) 0
                else if (spec.everyWindmill) mills.minOf { state.windmillTicks[it] }
                else mills.maxOf { state.windmillTicks[it] }
            }

            Metric.FloodedTiles -> cells.indices.count {
                val t = cells[it].terrain
                (t == TerrainType.Village || t == TerrainType.Crop || t == TerrainType.Road) && state.water[it] >= 3
            }

            Metric.WetlandWater -> cells.indices
                .filter { cells[it].terrain == TerrainType.Wetland }
                .sumOf { state.water[it] }
        }
    }

    fun progress(level: Level, state: SimState): List<ObjectiveProgress> =
        level.objectives.map { spec ->
            val v = measure(spec, level, state)
            val met = when (spec.cmp) {
                Cmp.Eq -> v == spec.target
                Cmp.Gte -> v >= spec.target
                Cmp.Lte -> v <= spec.target
            }
            ObjectiveProgress(spec, v, met)
        }

    fun solved(level: Level, state: SimState): Boolean = progress(level, state).all { it.met }
}
