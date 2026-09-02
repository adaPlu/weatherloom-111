package com.rork.weatherloom.core.sim

/** Terrain kinds the deterministic simulation understands. */
enum class TerrainType {
    Meadow,
    Crop,
    Village,
    Reservoir,
    River,
    Lake,
    Wetland,
    Forest,
    Mountain,
    Stone,
    Road,
    BareSoil;

    val isWaterBody: Boolean
        get() = this == Reservoir || this == Lake

    /** Water flows freely along these without needing a downhill step. */
    val isChannel: Boolean
        get() = this == River || this == Wetland || isWaterBody
}

/** Decorative-but-simulated features placed on top of a terrain cell. */
enum class Feature { None, Flower, Windmill, House }

/** Cardinal directions only; index order N, E, S, W is the deterministic tie-break order. */
enum class Dir(val dx: Int, val dy: Int) {
    None(0, 0),
    North(0, -1),
    East(1, 0),
    South(0, 1),
    West(-1, 0)
}

val CARDINALS: List<Dir> = listOf(Dir.North, Dir.East, Dir.South, Dir.West)

/** Immutable per-cell authored data. */
data class LevelCell(
    val terrain: TerrainType,
    val elevation: Int,
    val feature: Feature = Feature.None
)
