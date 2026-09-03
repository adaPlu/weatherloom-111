package com.rork.weatherloom.ui.puzzle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.weatherloom.audio.LoomAudio
import com.rork.weatherloom.audio.Sfx
import com.rork.weatherloom.core.level.DailyForecast
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.core.level.LevelLibrary
import com.rork.weatherloom.core.sim.EventKind
import com.rork.weatherloom.core.sim.ObjectiveProgress
import com.rork.weatherloom.core.sim.Objectives
import com.rork.weatherloom.core.sim.SimResult
import com.rork.weatherloom.core.sim.SimState
import com.rork.weatherloom.core.sim.SimulationEngine
import com.rork.weatherloom.core.sim.ThreadType
import com.rork.weatherloom.core.sim.WeatherThread
import com.rork.weatherloom.data.GameRepository
import com.rork.weatherloom.data.Rating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase { Draw, Playback, Result }

data class PuzzleUiState(
    val level: Level? = null,
    val phase: Phase = Phase.Draw,
    val threads: List<WeatherThread> = emptyList(),
    val redoStack: List<WeatherThread> = emptyList(),
    val armed: ThreadType? = null,
    val result: SimResult? = null,
    val beat: Int = 0,
    val playing: Boolean = false,
    val speed: Int = 1,
    val rating: Rating = Rating.None,
    val newCollectible: String? = null,
    val explanation: List<String> = emptyList(),
    val inspectCell: Int? = null,
    val showTemperature: Boolean = false,
    val hintShown: Boolean = false,
    val isDaily: Boolean = false,
    val simulating: Boolean = false,
    val simulatedThreads: List<WeatherThread> = emptyList()
) {
    val remaining: Map<ThreadType, Int>
        get() {
            val lvl = level ?: return emptyMap()
            val used = threads.groupingBy { it.type }.eachCount()
            return lvl.threads.mapValues { (type, n) -> n - (used[type] ?: 0) }
        }

    val canSimulate: Boolean get() = threads.isNotEmpty() && !simulating

    /** The state the board should render right now. */
    fun frame(): SimState? {
        val r = result
        val lvl = level ?: return null
        return if (r != null && phase != Phase.Draw) {
            r.frames.getOrNull(beat.coerceIn(0, r.frames.size - 1))
        } else {
            SimulationEngine.initialState(lvl)
        }
    }

    fun progress(): List<ObjectiveProgress> {
        val lvl = level ?: return emptyList()
        val f = frame() ?: return emptyList()
        return Objectives.progress(lvl, f)
    }

    val strokeCells: Int get() = threads.sumOf { it.cells.size }
    val simulatedStrokeCells: Int get() = simulatedThreads.sumOf { it.cells.size }
}

/** Owns one puzzle attempt: drawing, running the sim, scrubbing it, and scoring it. */
class PuzzleViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GameRepository.get(app)
    private val _ui = MutableStateFlow(PuzzleUiState())
    val ui: StateFlow<PuzzleUiState> = _ui.asStateFlow()

    private var playJob: Job? = null
    private var lastScoredBeat = -1

    init {
        // One place that keeps the weather you can hear in step with the weather you see.
        viewModelScope.launch {
            _ui.collect { s -> followAudio(s) }
        }
    }

    fun load(levelId: String) {
        if (_ui.value.level?.id == levelId) return
        val daily = levelId.startsWith("daily-")
        val level = if (daily) {
            DailyForecast.forDay(levelId.removePrefix("daily-"))
        } else {
            LevelLibrary.level(levelId)
        } ?: return
        // First hollow ever opened: lead with the rule rather than let them flounder.
        val firstEver = !daily &&
            LevelLibrary.indexOf(level.id) == 0 &&
            !repo.save.value.tutorialSeen
        if (firstEver) repo.markTutorialSeen()
        _ui.value = PuzzleUiState(
            level = level,
            armed = level.threads.keys.firstOrNull(),
            isDaily = daily,
            hintShown = firstEver
        )
    }

    fun arm(type: ThreadType) {
        if (_ui.value.simulating) return
        _ui.value = _ui.value.copy(armed = type)
    }

    fun addThread(thread: WeatherThread) {
        val s = _ui.value
        if (s.simulating) return
        val level = s.level ?: return
        val used = s.threads.count { it.type == thread.type }
        val budget = level.threads[thread.type] ?: 0
        if (used >= budget) return
        val threads = s.threads + thread
        // auto-arm the next type that still has uses left
        val nextArmed = if (used + 1 >= budget) {
            level.threads.entries.firstOrNull { (t, n) ->
                threads.count { it.type == t } < n
            }?.key
        } else thread.type
        _ui.value = s.copy(threads = threads, redoStack = emptyList(), armed = nextArmed ?: s.armed)
    }

    fun undo() {
        val s = _ui.value
        if (s.simulating) return
        val last = s.threads.lastOrNull() ?: return
        _ui.value = s.copy(
            threads = s.threads.dropLast(1),
            redoStack = s.redoStack + last,
            armed = last.type
        )
    }

    fun redo() {
        val s = _ui.value
        if (s.simulating) return
        val last = s.redoStack.lastOrNull() ?: return
        _ui.value = s.copy(threads = s.threads + last, redoStack = s.redoStack.dropLast(1))
    }

    fun clear() {
        val s = _ui.value
        if (s.simulating || s.threads.isEmpty()) return
        _ui.value = s.copy(
            threads = emptyList(),
            redoStack = emptyList(),
            armed = s.level?.threads?.keys?.firstOrNull()
        )
    }

    fun toggleHint() {
        _ui.value = _ui.value.copy(hintShown = !_ui.value.hintShown)
    }

    fun toggleTemperature() {
        _ui.value = _ui.value.copy(showTemperature = !_ui.value.showTemperature)
    }

    fun inspect(cell: Int?) {
        _ui.value = _ui.value.copy(inspectCell = cell)
    }

    /** Reveals the canonical solution, for when a player is truly stuck. */
    fun revealSolution() {
        if (_ui.value.simulating) return
        val level = _ui.value.level ?: return
        if (level.solution.isEmpty()) return
        val threads = level.solution.map { s ->
            val cells = bresenham(s.fromX, s.fromY, s.toX, s.toY, level.width)
            WeatherThread(
                type = s.type,
                points = cells.map { c ->
                    Pair(
                        (c % level.width + 0.5f) / level.width,
                        (c / level.width + 0.5f) / level.height
                    )
                },
                cells = cells,
                dir = strokeDir(s.fromX, s.fromY, s.toX, s.toY)
            )
        }
        _ui.value = _ui.value.copy(threads = threads, redoStack = emptyList(), hintShown = false)
    }

    fun simulate() {
        val s = _ui.value
        val level = s.level ?: return
        if (s.threads.isEmpty() || s.simulating) return

        // Freeze the attempt before leaving the main thread. A simulation result must
        // always be scored against the exact weave that produced it.
        val runThreads = s.threads.toList()
        repo.recordAttempt(level.id)
        LoomAudio.play(Sfx.LoomStart)
        lastScoredBeat = -1
        _ui.value = s.copy(simulating = true, simulatedThreads = emptyList())

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                SimulationEngine.run(level, runThreads)
            }
            val current = _ui.value
            if (current.level?.id != level.id) return@launch
            _ui.value = current.copy(
                threads = runThreads,
                simulatedThreads = runThreads,
                simulating = false,
                result = result,
                phase = Phase.Playback,
                beat = 0,
                playing = true,
                speed = 1
            )
            play()
        }
    }

    fun togglePlay() {
        val s = _ui.value
        if (s.playing) {
            playJob?.cancel()
            _ui.value = s.copy(playing = false)
        } else {
            val atEnd = s.result != null && s.beat >= s.result.beats
            _ui.value = s.copy(playing = true, beat = if (atEnd) 0 else s.beat)
            play()
        }
    }

    fun cycleSpeed() {
        val s = _ui.value
        val next = when (s.speed) {
            1 -> 2
            2 -> 4
            else -> 1
        }
        _ui.value = s.copy(speed = next)
        if (s.playing) play()
    }

    fun restart() {
        _ui.value = _ui.value.copy(beat = 0, playing = true)
        play()
    }

    fun scrubTo(beat: Int) {
        playJob?.cancel()
        val max = _ui.value.result?.beats ?: 0
        _ui.value = _ui.value.copy(beat = beat.coerceIn(0, max), playing = false)
    }

    fun stepBeat(delta: Int) = scrubTo(_ui.value.beat + delta)

    private fun play() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (true) {
                val s = _ui.value
                val result = s.result ?: return@launch
                if (!s.playing) return@launch
                if (s.beat >= result.beats) {
                    _ui.value = s.copy(playing = false)
                    finish()
                    return@launch
                }
                delay((250L / s.speed).coerceAtLeast(40L))
                val cur = _ui.value
                if (!cur.playing) return@launch
                _ui.value = cur.copy(beat = (cur.beat + 1).coerceAtMost(result.beats))
            }
        }
    }

    /** Scores the run, saves it, and raises the result sheet. */
    private fun finish() {
        val s = _ui.value
        val level = s.level ?: return
        val result = s.result ?: return
        val scoredThreads = s.simulatedThreads.ifEmpty { s.threads }
        val scoredCells = scoredThreads.sumOf { it.cells.size }
        val rating = when {
            !result.solved -> Rating.None
            scoredThreads.size <= level.flourishStrokes && scoredCells <= level.flourishCells -> Rating.Flourish
            scoredThreads.size <= level.bloomStrokes -> Rating.Bloom
            else -> Rating.Seedling
        }
        var unlocked: String? = null
        if (result.solved && !s.isDaily) {
            val newly = repo.recordSolve(level.id, rating, scoredThreads.size, scoredCells, level.reward)
            if (newly) unlocked = level.reward
        }
        if (result.solved && s.isDaily) {
            repo.recordDaily(level.id.removePrefix("daily-"))
        }
        _ui.value = _ui.value.copy(
            phase = Phase.Result,
            rating = rating,
            newCollectible = unlocked,
            explanation = SimulationEngine.explain(level, result)
        )
        LoomAudio.silenceAmbience()
        if (result.solved) {
            LoomAudio.play(Sfx.Bloom)
            if (unlocked != null) {
                viewModelScope.launch {
                    delay(900)
                    LoomAudio.play(Sfx.Collect)
                }
            }
        } else {
            LoomAudio.play(Sfx.Wilt, 0.85f)
        }
    }

    /** Jumps the timeline to a causal event and pauses there. */
    fun jumpToEvent(beat: Int, cell: Int) {
        playJob?.cancel()
        _ui.value = _ui.value.copy(
            beat = beat.coerceIn(0, _ui.value.result?.beats ?: 0),
            playing = false,
            phase = Phase.Playback,
            inspectCell = cell
        )
    }

    fun backToDraw() {
        playJob?.cancel()
        lastScoredBeat = -1
        _ui.value = _ui.value.copy(
            phase = Phase.Draw,
            playing = false,
            beat = 0,
            result = null,
            simulatedThreads = emptyList(),
            inspectCell = null
        )
    }

    fun dismissResult() {
        _ui.value = _ui.value.copy(phase = Phase.Playback, playing = false)
    }

    fun replay() {
        lastScoredBeat = -1
        _ui.value = _ui.value.copy(phase = Phase.Playback, beat = 0, playing = true)
        play()
    }

    /**
     * Drives the ambience beds from the frame on screen and fires a one-shot for
     * causal events as the playhead passes them. Scrubbing moves the ambience but
     * stays quiet on cues, so dragging the timeline never machine-guns the speaker.
     */
    private fun followAudio(s: PuzzleUiState) {
        val level = s.level
        if (level == null || s.phase == Phase.Draw) {
            LoomAudio.silenceAmbience()
            lastScoredBeat = -1
            return
        }
        val frame = s.frame() ?: return
        val cells = frame.size.coerceAtLeast(1)

        var raining = 0
        var windSum = 0
        for (i in 0 until cells) {
            if (frame.precip[i] > 0) raining++
            windSum += frame.windStr[i]
        }
        // A handful of raining tiles should already be clearly audible, so the
        // fraction is lifted well before it is clamped.
        val rainLevel = (raining.toFloat() / cells * 4.5f).coerceIn(0f, 1f)
        val windLevel = (windSum.toFloat() / cells / 2f).coerceIn(0f, 1f)
        LoomAudio.setAmbience(rainLevel, windLevel)

        val result = s.result ?: return
        if (!s.playing) {
            lastScoredBeat = s.beat
            return
        }
        if (s.beat <= lastScoredBeat) {
            lastScoredBeat = s.beat
            return
        }
        val from = lastScoredBeat
        lastScoredBeat = s.beat
        val crossed = result.events.filter { it.beat in (from + 1)..s.beat }
        // At most one cue per beat window: overlapping chimes stop sounding cozy.
        crossed.firstNotNullOfOrNull { it.kind.cue() }?.let { (sfx, gain) ->
            LoomAudio.play(sfx, gain)
        }
    }

    override fun onCleared() {
        playJob?.cancel()
        super.onCleared()
    }
}

/** Straight-line rasterisation shared with the level validator. */
internal fun bresenham(x0: Int, y0: Int, x1: Int, y1: Int, width: Int): List<Int> {
    var cx = x0
    var cy = y0
    val out = ArrayList<Int>()
    val dx = kotlin.math.abs(x1 - x0)
    val dy = kotlin.math.abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx - dy
    while (true) {
        out.add(cy * width + cx)
        if (cx == x1 && cy == y1) break
        val e2 = 2 * err
        if (e2 > -dy) {
            err -= dy
            cx += sx
        }
        if (e2 < dx) {
            err += dx
            cy += sy
        }
    }
    return out
}

/** Which one-shot, if any, a causal event deserves. */
private fun EventKind.cue(): Pair<Sfx, Float>? = when (this) {
    EventKind.CropFrozen -> Sfx.Frost to 0.9f
    EventKind.SnowBegins -> Sfx.Frost to 0.55f
    EventKind.FlowerBloomed -> Sfx.Bloom to 0.45f
    EventKind.Overflow, EventKind.Flooded -> Sfx.Wilt to 0.5f
    else -> null
}

internal fun strokeDir(x0: Int, y0: Int, x1: Int, y1: Int): com.rork.weatherloom.core.sim.Dir {
    val dx = x1 - x0
    val dy = y1 - y0
    return when {
        kotlin.math.abs(dx) >= kotlin.math.abs(dy) ->
            if (dx > 0) com.rork.weatherloom.core.sim.Dir.East
            else if (dx < 0) com.rork.weatherloom.core.sim.Dir.West
            else com.rork.weatherloom.core.sim.Dir.None

        dy > 0 -> com.rork.weatherloom.core.sim.Dir.South
        else -> com.rork.weatherloom.core.sim.Dir.North
    }
}
