package com.rork.weatherloom.data

import android.content.Context
import android.content.SharedPreferences
import com.rork.weatherloom.core.level.LevelLibrary
import com.rork.weatherloom.core.terrarium.GrowthState
import com.rork.weatherloom.core.terrarium.PlayerInventory
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumLayout
import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.terrarium.reaction.ReactionCatalog
import com.rork.weatherloom.core.terrarium.reaction.ReactionEngine
import com.rork.weatherloom.core.terrarium.reaction.ReactionResult
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Seedling = solved, Bloom = solved efficiently, Flourish = the expert line. */
enum class Rating { None, Seedling, Bloom, Flourish }

@Serializable
data class LevelRecord(
    val rating: Int = 0,
    val attempts: Int = 0,
    val bestStrokes: Int = 0,
    val bestCells: Int = 0
) {
    val ratingEnum: Rating get() = Rating.entries[rating.coerceIn(0, 3)]
}

@Serializable
data class SaveData(
    val schema: Int = CURRENT_SAVE_SCHEMA,
    val levels: Map<String, LevelRecord> = emptyMap(),
    val collectibles: List<String> = emptyList(),
    val dailyHistory: List<String> = emptyList(),
    val lastCollectible: String? = null,
    val tutorialSeen: Boolean = false,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val musicEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val playerProgression: PlayerProgression = PlayerProgression(),
    val terrariumInventory: PlayerInventory = PlayerInventory(),
    val terrariumLayout: TerrariumLayout = TerrariumLayout(),
    val terrariumGrowth: List<GrowthState> = emptyList(),
    val terrariumEnvironment: EnvironmentState? = null,
    val appliedTerrariumReactionEventIds: List<String> = emptyList(),
    val terrariumDiscoveries: List<String> = emptyList()
)

/**
 * Local-first save. Everything the game needs works with no network, no account
 * and no credentials of any kind.
 */
class GameRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("weatherloom", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val terrariumCatalog = TerrariumCatalog.decode(
        context.assets.open("terrarium_items.json").bufferedReader().use { it.readText() }
    )
    private val reactionCatalog = ReactionCatalog.decode(
        context.assets.open("terrarium_reactions.json").bufferedReader().use { it.readText() }
    )
    private val puzzleSolveService = PuzzleSolveService(
        PuzzleRewardBridge(terrariumCatalog)
    )
    private val terrariumReactionSaveService = TerrariumReactionSaveService(
        terrariumCatalog,
        reactionCatalog
    )

    private val _save = MutableStateFlow(read())
    val save: StateFlow<SaveData> = _save.asStateFlow()

    /** Every mutation goes through this gate before later reward/inventory fields are added. */
    private val mutator = SaveStateMutator(_save.value, ::persist)

    private fun read(): SaveData =
        SaveMigration.decode(prefs.getString(KEY, null), json)

    private fun persist(data: SaveData) {
        _save.value = data
        runCatching {
            prefs.edit().putString(KEY, json.encodeToString(SaveData.serializer(), data)).apply()
        }
    }

    fun recordAttempt(levelId: String) {
        mutator.mutate { current ->
            val rec = current.levels[levelId] ?: LevelRecord()
            current.copy(
                levels = current.levels +
                    (levelId to rec.copy(attempts = rec.attempts + 1))
            )
        }
    }

    /** Stores a win, upgrades the rating, and atomically grants its Terrarium reward/echo. */
    fun recordSolve(
        levelId: String,
        rating: Rating,
        strokes: Int,
        cells: Int,
        reward: String?,
        weatherEcho: WeatherEchoSnapshot? = null
    ): Boolean =
        mutator.mutateWithResult { current ->
            val result = puzzleSolveService.recordSolve(
                save = current,
                levelId = levelId,
                rating = rating,
                strokes = strokes,
                cells = cells,
                rewardId = reward,
                weatherEcho = weatherEcho
            )
            result.save to result.newlyUnlockedCollectible
        }

    /**
     * Recomputes ephemeral Terrarium reactions and applies any new durable event IDs through
     * the same serialized save gate. Calling this repeatedly is safe and idempotent.
     */
    fun evaluateTerrariumReactions(): ReactionResult =
        mutator.mutateWithResult { current ->
            val result = terrariumReactionSaveService.evaluateAndApply(current)
            result.save to result.reactions
        }

    fun recordDaily(dayKey: String) {
        mutator.mutate { current ->
            if (dayKey in current.dailyHistory) current
            else current.copy(dailyHistory = (current.dailyHistory + dayKey).takeLast(120))
        }
    }

    fun markTutorialSeen() {
        mutator.mutate { current ->
            if (current.tutorialSeen) current else current.copy(tutorialSeen = true)
        }
    }

    fun setReducedMotion(value: Boolean) =
        mutator.mutate { it.copy(reducedMotion = value) }

    fun setMusicEnabled(value: Boolean) =
        mutator.mutate { it.copy(musicEnabled = value) }

    fun setSoundEnabled(value: Boolean) =
        mutator.mutate { it.copy(soundEnabled = value) }

    fun setHighContrast(value: Boolean) =
        mutator.mutate { it.copy(highContrast = value) }

    fun resetProgress() {
        mutator.mutate { SaveData() }
    }

    // ------------------------------------------------------------ derived data

    /**
     * Recomputable reaction presentation for the current save. This accessor is deliberately
     * read-only: durable discovery ownership remains with [evaluateTerrariumReactions].
     */
    fun terrariumReactionSnapshot(): ReactionResult {
        val current = _save.value
        val environment = current.terrariumEnvironment ?: return ReactionResult()
        return ReactionEngine.evaluate(
            layout = current.terrariumLayout,
            growthStates = current.terrariumGrowth,
            environment = environment,
            catalog = terrariumCatalog,
            reactions = reactionCatalog,
            appliedDurableEventIds = current.appliedTerrariumReactionEventIds.toSet()
        )
    }

    /** Static authored Terrarium metadata for read-only presentation/query projections. */
    fun terrariumCatalogSnapshot(): TerrariumCatalog = terrariumCatalog

    fun ratingOf(levelId: String): Rating = _save.value.levels[levelId]?.ratingEnum ?: Rating.None

    fun isSolved(levelId: String): Boolean = ratingOf(levelId) != Rating.None

    /** A level is playable once the one before it is solved; the first is always open. */
    fun isUnlocked(levelId: String): Boolean {
        val i = LevelLibrary.indexOf(levelId)
        if (i <= 0) return true
        return isSolved(LevelLibrary.levels[i - 1].id)
    }

    fun nextUnsolved(): String? =
        LevelLibrary.levels.firstOrNull { !isSolved(it.id) }?.id
            ?: LevelLibrary.levels.lastOrNull()?.id

    fun solvedCount(): Int = _save.value.levels.count { it.value.rating > 0 }

    /** Consecutive daily challenges finishing today or yesterday. */
    fun dailyStreak(todayKey: String, yesterdayKeys: List<String>): Int {
        val done = _save.value.dailyHistory.toSet()
        var streak = 0
        if (todayKey in done) streak++
        for (k in yesterdayKeys) {
            if (k in done) streak++ else break
        }
        return streak
    }

    companion object {
        private const val KEY = "save_v1"

        @Volatile
        private var instance: GameRepository? = null

        fun get(context: Context): GameRepository =
            instance ?: synchronized(this) {
                instance ?: GameRepository(context.applicationContext).also { instance = it }
            }
    }
}
