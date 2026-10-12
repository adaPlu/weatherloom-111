package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot

/** Durable result of recording a solved authored puzzle. */
data class PuzzleSolveResult(
    val save: SaveData,
    val newlyUnlockedCollectible: Boolean,
    val newTerrariumItem: Boolean,
    val xpGranted: Int
)

/**
 * Pure solved-level reducer. Progress, XP, legacy collectible presentation state,
 * Terrarium ownership, and the latest deterministic Weather Echo are produced as one
 * new [SaveData] value so the repository persists them atomically through its existing
 * [SaveStateMutator].
 */
class PuzzleSolveService(
    private val rewardBridge: PuzzleRewardBridge
) {
    fun recordSolve(
        save: SaveData,
        levelId: String,
        rating: Rating,
        strokes: Int,
        cells: Int,
        rewardId: String?,
        weatherEcho: WeatherEchoSnapshot? = null
    ): PuzzleSolveResult {
        val record = save.levels[levelId] ?: LevelRecord()
        val bestRating = maxOf(record.rating, rating.ordinal)
        val updatedRecord = record.copy(
            rating = bestRating,
            bestStrokes = if (record.bestStrokes == 0) strokes else minOf(record.bestStrokes, strokes),
            bestCells = if (record.bestCells == 0) cells else minOf(record.bestCells, cells)
        )

        val bestRatingEnum = Rating.entries[bestRating.coerceIn(0, Rating.entries.lastIndex)]
        val xpGrant = save.playerProgression.grantForLevelRating(levelId, bestRatingEnum)

        val newlyUnlockedCollectible = rewardId != null && rewardId !in save.collectibles
        val progressSave = save.copy(
            levels = save.levels + (levelId to updatedRecord),
            playerProgression = xpGrant.progression,
            collectibles = if (newlyUnlockedCollectible) {
                save.collectibles + requireNotNull(rewardId)
            } else {
                save.collectibles
            },
            lastCollectible = if (newlyUnlockedCollectible) rewardId else save.lastCollectible,
            terrariumEnvironment = weatherEcho
                ?.let { EnvironmentState(weatherEcho = it) }
                ?: save.terrariumEnvironment
        )

        val terrariumReward = rewardBridge.grant(
            save = progressSave,
            levelId = levelId,
            rewardId = rewardId
        )

        return PuzzleSolveResult(
            save = terrariumReward.save,
            newlyUnlockedCollectible = newlyUnlockedCollectible,
            newTerrariumItem = terrariumReward.newTerrariumItem,
            xpGranted = xpGrant.xpGranted
        )
    }
}
