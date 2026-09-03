package com.rork.weatherloom.core.sim

import com.rork.weatherloom.core.level.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationDeterminismTest {

    private fun testLevel(): Level = Level(
        id = "determinism-test",
        chapter = 1,
        name = "Determinism Test",
        brief = "",
        hint = "",
        width = 2,
        height = 2,
        cells = List(4) { LevelCell(TerrainType.Meadow, 0) },
        baseTemp = 0,
        startMoisture = 1,
        startFog = IntArray(4),
        startReservoir = 0,
        reservoirCapacity = 8,
        startSnowSummits = false,
        bloomTempRange = -1..1,
        riverFlow = Dir.South,
        beats = 8,
        threads = mapOf(ThreadType.WarmFront to 1),
        maxThreadCells = 4,
        objectives = listOf(
            ObjectiveSpec(Metric.FrozenCrops, Cmp.Lte, 0, "Safe")
        ),
        bloomStrokes = 1,
        flourishStrokes = 1,
        flourishCells = 2,
        reward = null,
        solution = emptyList()
    )

    @Test
    fun identicalInputsProduceIdenticalFramesAndEndHash() {
        val level = testLevel()
        val thread = WeatherThread(
            type = ThreadType.WarmFront,
            points = listOf(0.25f to 0.25f, 0.75f to 0.25f),
            cells = listOf(0, 1),
            dir = Dir.East
        )

        val first = SimulationEngine.run(level, listOf(thread))
        val second = SimulationEngine.run(level, listOf(thread))

        assertTrue(first.solved)
        assertEquals(first.endHash, second.endHash)
        assertEquals(first.frames.map { it.hash() }, second.frames.map { it.hash() })
        assertEquals(first.events, second.events)
    }
}
