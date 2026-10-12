package com.rork.weatherloom.core.level

import com.rork.weatherloom.core.sim.Cmp
import com.rork.weatherloom.core.sim.Dir
import com.rork.weatherloom.core.sim.Feature
import com.rork.weatherloom.core.sim.LevelCell
import com.rork.weatherloom.core.sim.Metric
import com.rork.weatherloom.core.sim.ObjectiveSpec
import com.rork.weatherloom.core.sim.TerrainType
import com.rork.weatherloom.core.sim.ThreadType

/** A puzzle board plus everything the simulation and the UI need to present it. */
data class Level(
    val id: String,
    val chapter: Int,
    val name: String,
    val brief: String,
    val hint: String,
    val width: Int,
    val height: Int,
    val cells: List<LevelCell>,
    val baseTemp: Int,
    val startMoisture: Int,
    val startFog: IntArray,
    val startReservoir: Int,
    val reservoirCapacity: Int,
    val startSnowSummits: Boolean,
    val bloomTempRange: IntRange,
    val riverFlow: Dir,
    val beats: Int,
    val threads: Map<ThreadType, Int>,
    val maxThreadCells: Int,
    val objectives: List<ObjectiveSpec>,
    val bloomStrokes: Int,
    val flourishStrokes: Int,
    val flourishCells: Int,
    val reward: String?,
    val solution: List<SolutionStroke>
) {
    val size: Int get() = width * height
    val startFogTotal: Int by lazy { startFog.sum() }
    val reservoirCells: List<Int> by lazy {
        cells.indices.filter { cells[it].terrain == TerrainType.Reservoir }
    }
    val totalThreads: Int get() = threads.values.sum()

    /** Objectives never displayed above their target keeps the chips readable. */
    fun displayTarget(spec: ObjectiveSpec, value: Int): Int =
        if (spec.cmp == Cmp.Gte) minOf(value, spec.target) else value

    override fun equals(other: Any?): Boolean = other is Level && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

data class SolutionStroke(
    val type: ThreadType,
    val fromX: Int,
    val fromY: Int,
    val toX: Int,
    val toY: Int
)

data class Chapter(val index: Int, val title: String, val subtitle: String)

data class Collectible(
    val id: String,
    val name: String,
    val flavour: String,
    val unlock: String,
    val biome: String
)

/** Map characters shared with tools/validate_levels.py. */
object MapChars {
    fun parse(ch: Char): Pair<TerrainType, Feature> = when (ch) {
        '.' -> TerrainType.BareSoil to Feature.None
        'm' -> TerrainType.Meadow to Feature.None
        'f' -> TerrainType.Meadow to Feature.Flower
        'c' -> TerrainType.Crop to Feature.None
        'v' -> TerrainType.Village to Feature.None
        'h' -> TerrainType.Village to Feature.House
        'R' -> TerrainType.Reservoir to Feature.None
        '~' -> TerrainType.River to Feature.None
        'L' -> TerrainType.Lake to Feature.None
        'w' -> TerrainType.Wetland to Feature.None
        'T' -> TerrainType.Forest to Feature.None
        '^' -> TerrainType.Mountain to Feature.None
        's' -> TerrainType.Stone to Feature.None
        '-' -> TerrainType.Road to Feature.None
        'W' -> TerrainType.Meadow to Feature.Windmill
        'X' -> TerrainType.Stone to Feature.Windmill
        else -> TerrainType.Meadow to Feature.None
    }
}

internal fun metricOf(name: String): Metric = Metric.valueOf(name)
internal fun cmpOf(name: String): Cmp = Cmp.valueOf(name)
internal fun threadOf(name: String): ThreadType = ThreadType.valueOf(name)
