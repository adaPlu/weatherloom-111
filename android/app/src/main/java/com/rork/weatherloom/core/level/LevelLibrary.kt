package com.rork.weatherloom.core.level

import android.content.Context
import com.rork.weatherloom.core.sim.Dir
import com.rork.weatherloom.core.sim.LevelCell
import com.rork.weatherloom.core.sim.ObjectiveSpec
import com.rork.weatherloom.core.sim.ThreadType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LevelFile(
    val version: Int,
    val chapters: List<ChapterDto>,
    val levels: List<LevelDto>,
    val collectibles: List<CollectibleDto>
)

@Serializable
private data class ChapterDto(val index: Int, val title: String, val subtitle: String)

@Serializable
private data class CollectibleDto(
    val id: String,
    val name: String,
    val flavour: String,
    val unlock: String,
    val biome: String
)

@Serializable
private data class ObjectiveDto(
    val metric: String,
    val cmp: String,
    val target: Int,
    val label: String,
    val everyWindmill: Boolean = false
)

@Serializable
private data class StrokeDto(val type: String, val from: List<Int>, val to: List<Int>)

@Serializable
private data class LevelDto(
    val id: String,
    val chapter: Int,
    val name: String,
    val brief: String,
    val hint: String,
    val map: List<String>,
    val elevation: List<String>,
    val fog: List<String>? = null,
    val baseTemp: Int,
    val startMoisture: Int,
    val startReservoir: Int = 0,
    val reservoirCapacity: Int = 8,
    val startSnowSummits: Boolean = false,
    val bloomTempRange: List<Int>,
    val riverFlow: String = "South",
    val beats: Int,
    val threads: Map<String, Int>,
    val maxThreadCells: Int,
    val objectives: List<ObjectiveDto>,
    val bloomStrokes: Int,
    val flourishStrokes: Int,
    val flourishCells: Int,
    val reward: String? = null,
    val solution: List<StrokeDto> = emptyList(),
    @SerialName("_note") val note: String? = null
)

/** Loads and caches the authored content. Levels are data, never code. */
object LevelLibrary {
    private val json = Json { ignoreUnknownKeys = true }

    private var loaded = false
    private var _levels: List<Level> = emptyList()
    private var _chapters: List<Chapter> = emptyList()
    private var _collectibles: List<Collectible> = emptyList()

    val levels: List<Level> get() = _levels
    val chapters: List<Chapter> get() = _chapters
    val collectibles: List<Collectible> get() = _collectibles

    fun load(context: Context) {
        if (loaded) return
        val text = context.assets.open("levels.json").bufferedReader().use { it.readText() }
        val file = json.decodeFromString(LevelFile.serializer(), text)
        _chapters = file.chapters.map { Chapter(it.index, it.title, it.subtitle) }
        _collectibles = file.collectibles.map { Collectible(it.id, it.name, it.flavour, it.unlock, it.biome) }
        _levels = file.levels.map { it.toLevel() }
        loaded = true
    }

    fun level(id: String): Level? = _levels.firstOrNull { it.id == id }

    fun levelsOf(chapter: Int): List<Level> = _levels.filter { it.chapter == chapter }

    fun collectible(id: String): Collectible? = _collectibles.firstOrNull { it.id == id }

    fun indexOf(id: String): Int = _levels.indexOfFirst { it.id == id }

    fun next(id: String): Level? {
        val i = indexOf(id)
        return if (i >= 0 && i + 1 < _levels.size) _levels[i + 1] else null
    }

    private fun LevelDto.toLevel(): Level {
        val h = map.size
        val w = map[0].length
        val cells = ArrayList<LevelCell>(w * h)
        for (y in 0 until h) {
            val row = map[y]
            val elevRow = elevation[y]
            for (x in 0 until w) {
                val (terrain, feature) = MapChars.parse(row[x])
                cells.add(LevelCell(terrain, elevRow[x].digitToInt(), feature))
            }
        }
        val fogArray = IntArray(w * h)
        fog?.let { rows ->
            var i = 0
            for (row in rows) for (ch in row) fogArray[i++] = ch.digitToInt()
        }
        return Level(
            id = id,
            chapter = chapter,
            name = name,
            brief = brief,
            hint = hint,
            width = w,
            height = h,
            cells = cells,
            baseTemp = baseTemp,
            startMoisture = startMoisture,
            startFog = fogArray,
            startReservoir = startReservoir,
            reservoirCapacity = reservoirCapacity,
            startSnowSummits = startSnowSummits,
            bloomTempRange = bloomTempRange[0]..bloomTempRange[1],
            riverFlow = Dir.valueOf(riverFlow),
            beats = beats,
            threads = threads.mapKeys { ThreadType.valueOf(it.key) },
            maxThreadCells = maxThreadCells,
            objectives = objectives.map {
                ObjectiveSpec(metricOf(it.metric), cmpOf(it.cmp), it.target, it.label, it.everyWindmill)
            },
            bloomStrokes = bloomStrokes,
            flourishStrokes = flourishStrokes,
            flourishCells = flourishCells,
            reward = reward,
            solution = solution.map {
                SolutionStroke(threadOf(it.type), it.from[0], it.from[1], it.to[0], it.to[1])
            }
        )
    }
}
