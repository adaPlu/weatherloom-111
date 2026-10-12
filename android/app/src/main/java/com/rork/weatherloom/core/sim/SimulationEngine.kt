package com.rork.weatherloom.core.sim

import com.rork.weatherloom.core.level.Level

/** Version stamped into replays; bump whenever a rule changes. */
const val SIM_VERSION: Int = 1

/** Result of running a whole simulation headlessly. */
data class SimResult(
    val frames: List<SimState>,
    val events: List<CausalEvent>,
    val solved: Boolean,
    val progress: List<ObjectiveProgress>,
    val endHash: Long
) {
    val beats: Int get() = frames.size - 1
    val finalState: SimState get() = frames.last()
}

/**
 * The deterministic weather engine.
 *
 * Every beat resolves the same ten steps in the same order, reading from the previous
 * beat's values and writing into the next, so collection iteration order can never
 * change an outcome. Runs headlessly — no Android or Compose dependency.
 */
object SimulationEngine {

    fun run(level: Level, threads: List<WeatherThread>): SimResult {
        val size = level.size
        val frames = ArrayList<SimState>(level.beats + 1)
        val events = ArrayList<CausalEvent>()
        var state = initialState(level)
        frames.add(state.copy())

        val seen = HashSet<EventKind>()

        for (beat in 1..level.beats) {
            state = step(level, threads, state, beat, events, seen)
            frames.add(state.copy())
        }

        val progress = Objectives.progress(level, state)
        return SimResult(
            frames = frames,
            events = events.sortedWith(compareBy({ it.beat }, { it.kind.ordinal }, { it.cell })),
            solved = progress.all { it.met },
            progress = progress,
            endHash = state.hash()
        )
    }

    fun initialState(level: Level): SimState {
        val s = SimState(level.size)
        for (i in 0 until level.size) {
            val c = level.cells[i]
            s.temp[i] = baselineTemp(level, i)
            s.moisture[i] = if (c.terrain.isWaterBody || c.terrain == TerrainType.Wetland) 1 else level.startMoisture
            s.fog[i] = level.startFog[i]
            s.water[i] = if (c.terrain == TerrainType.River) 1 else 0
            s.storage[i] = if (c.terrain == TerrainType.Reservoir) level.startReservoir else 0
            s.snow[i] = if (c.terrain == TerrainType.Mountain && level.startSnowSummits && c.elevation >= 3) 1 else 0
        }
        return s
    }

    /** Higher ground is colder — two elevation steps shed one degree. */
    private fun baselineTemp(level: Level, i: Int): Int =
        (level.baseTemp - level.cells[i].elevation / 2).coerceIn(-2, 2)

    // ---------------------------------------------------------------- one beat

    private fun step(
        level: Level,
        threads: List<WeatherThread>,
        prev: SimState,
        beat: Int,
        events: MutableList<CausalEvent>,
        seen: MutableSet<EventKind>
    ): SimState {
        val w = level.width
        val h = level.height
        val size = level.size
        val s = prev.copy()
        s.beat = beat
        java.util.Arrays.fill(s.precip, 0)
        java.util.Arrays.fill(s.precipSnow, 0)
        java.util.Arrays.fill(s.windDir, Dir.None.ordinal)
        java.util.Arrays.fill(s.windStr, 0)

        fun idx(x: Int, y: Int): Int = y * w + x
        fun inBounds(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h
        fun neighbour(i: Int, d: Dir): Int {
            val x = i % w + d.dx
            val y = i / w + d.dy
            return if (inBounds(x, y)) idx(x, y) else -1
        }

        // Step 1 — persistent Weather Thread influence -------------------------
        val warmTouch = IntArray(size)
        val coldTouch = IntArray(size)
        for (t in threads) {
            when (t.type) {
                ThreadType.WarmFront -> {
                    for (c in t.cells) warmTouch[c] = 2
                    for (c in t.cells) for (d in CARDINALS) {
                        val n = neighbour(c, d)
                        if (n >= 0 && warmTouch[n] == 0) warmTouch[n] = 1
                    }
                }

                ThreadType.ColdFront -> {
                    for (c in t.cells) coldTouch[c] = 2
                    for (c in t.cells) for (d in CARDINALS) {
                        val n = neighbour(c, d)
                        if (n >= 0 && coldTouch[n] == 0) coldTouch[n] = 1
                    }
                }

                ThreadType.WindBand -> {
                    for (c in t.cells) {
                        s.windDir[c] = t.dir.ordinal
                        s.windStr[c] = 2
                    }
                    for (c in t.cells) for (d in CARDINALS) {
                        val n = neighbour(c, d)
                        if (n >= 0 && s.windStr[n] == 0) {
                            s.windDir[n] = t.dir.ordinal
                            s.windStr[n] = 1
                        }
                    }
                }

                ThreadType.MoistureRibbon -> {
                    for (c in t.cells) s.moisture[c] = minOf(3, s.moisture[c] + 1)
                    if (beat % 2 == 0) for (c in t.cells) for (d in CARDINALS) {
                        val n = neighbour(c, d)
                        if (n >= 0) s.moisture[n] = minOf(3, s.moisture[n] + 1)
                    }
                }
            }
        }

        for (i in 0 until size) {
            val warm = warmTouch[i]
            val cold = coldTouch[i]
            when {
                warm > 0 && cold > 0 -> Unit // fronts cancel where they cross; the collision is the point
                warm > 0 -> {
                    val target = if (warm == 2) 2 else 1
                    if (s.temp[i] < target) s.temp[i] = s.temp[i] + 1
                    if (warm == 2 && beat % 2 == 0) s.moisture[i] = minOf(3, s.moisture[i] + 1)
                }

                cold > 0 -> {
                    val target = if (cold == 2) -2 else -1
                    if (s.temp[i] > target) s.temp[i] = s.temp[i] - 1
                }

                beat % 2 == 0 -> {
                    // No front overhead: air drifts back to its natural temperature.
                    val base = baselineTemp(level, i)
                    if (s.temp[i] > base) s.temp[i]-- else if (s.temp[i] < base) s.temp[i]++
                }
            }
            // The shoulder of a warm front is humid too, just more slowly.
            if (warm == 1 && cold == 0 && beat % 4 == 0) {
                s.moisture[i] = minOf(3, s.moisture[i] + 1)
            }
        }

        // Step 2 — advect clouds and fog on the wind ---------------------------
        val cloudOut = IntArray(size)
        val fogOut = IntArray(size)
        for (i in 0 until size) {
            val str = s.windStr[i]
            val dir = Dir.entries[s.windDir[i]]
            if (str <= 0 || dir == Dir.None) {
                cloudOut[i] += s.cloud[i]
                fogOut[i] += s.fog[i]
                continue
            }
            val n = neighbour(i, dir)
            val moveCloud = minOf(s.cloud[i], str)
            val moveFog = if (str >= 1) minOf(s.fog[i], 1) else 0
            cloudOut[i] += s.cloud[i] - moveCloud
            fogOut[i] += s.fog[i] - moveFog
            if (n >= 0) {
                cloudOut[n] += moveCloud
                fogOut[n] += moveFog
            }
        }
        for (i in 0 until size) {
            s.cloud[i] = minOf(3, cloudOut[i])
            s.fog[i] = minOf(2, fogOut[i])
        }

        // Step 3 — air-mass / front interaction --------------------------------
        val prevCloud = IntArray(size)
        s.cloud.copyInto(prevCloud)
        for (i in 0 until size) {
            if (s.moisture[i] < 1) continue
            var maxDiff = 0
            for (d in CARDINALS) {
                val n = neighbour(i, d)
                if (n >= 0) {
                    val diff = kotlin.math.abs(s.temp[i] - s.temp[n])
                    if (diff > maxDiff) maxDiff = diff
                }
            }
            if (maxDiff >= 2) {
                s.cloud[i] = minOf(3, s.cloud[i] + 1)
                if (s.cloud[i] >= 2 && seen.add(EventKind.CloudsCollide)) {
                    events.add(
                        CausalEvent(beat, EventKind.CloudsCollide, i, "Beat $beat · warm and cold air met")
                    )
                }
            } else if (s.temp[i] <= -1 && s.moisture[i] >= 2) {
                s.cloud[i] = minOf(3, s.cloud[i] + 1)
            }
        }

        // Step 4 — terrain uplift ---------------------------------------------
        val uplift = IntArray(size)
        for (i in 0 until size) {
            if (s.windStr[i] <= 0) continue
            val n = neighbour(i, Dir.entries[s.windDir[i]])
            if (n >= 0 && level.cells[n].elevation > level.cells[i].elevation) uplift[n] = 1
        }

        // Step 5 + 6 — generate and deposit precipitation ----------------------
        for (i in 0 until size) {
            var intensity = 0
            if (s.cloud[i] >= 2 && s.moisture[i] >= 1) intensity = s.cloud[i] - 1
            if (uplift[i] == 1 && s.cloud[i] >= 1 && s.moisture[i] >= 1) intensity += 1
            if (intensity <= 0) continue
            intensity = minOf(intensity, 2)
            val snowing = s.temp[i] <= -1
            s.precip[i] = intensity
            s.precipSnow[i] = if (snowing) 1 else 0

            if (snowing) {
                s.snow[i] = minOf(2, s.snow[i] + 1)
                if (seen.add(EventKind.SnowBegins)) {
                    events.add(CausalEvent(beat, EventKind.SnowBegins, i, "Beat $beat · snow began falling"))
                }
            } else {
                if (level.cells[i].terrain.isWaterBody) {
                    s.storage[i] += intensity
                } else {
                    s.water[i] = minOf(3, s.water[i] + intensity)
                }
                if (seen.add(EventKind.RainBegins)) {
                    events.add(CausalEvent(beat, EventKind.RainBegins, i, "Beat $beat · rain began"))
                }
            }
            s.cloud[i] = maxOf(0, s.cloud[i] - intensity)
            s.moisture[i] = maxOf(0, s.moisture[i] - 1)
        }

        // Step 7 — runoff -------------------------------------------------------
        // Water moves toward the lowest surface it can see. Head = elevation*2 + depth,
        // so a puddle only spreads once it is deep, and channels always pull water in.
        val waterIn = IntArray(size)
        val waterOut = IntArray(size)
        for (i in 0 until size) {
            if (s.water[i] < 1) continue
            val cell = level.cells[i]
            if (cell.terrain.isWaterBody) continue
            var dest = -1

            if (cell.terrain == TerrainType.River) {
                val n = neighbour(i, level.riverFlow)
                if (n >= 0 && (level.cells[n].terrain.isChannel || level.cells[n].elevation < cell.elevation)) {
                    dest = n
                }
            }

            if (dest < 0) {
                val myHead = cell.elevation * 2 + s.water[i]
                var bestHead = myHead
                var best = -1
                for (d in CARDINALS) {
                    val n = neighbour(i, d)
                    if (n < 0) continue
                    val nc = level.cells[n]
                    val nHead = nc.elevation * 2 + s.water[n] - if (nc.terrain.isChannel) 2 else 0
                    if (nHead < bestHead) {
                        bestHead = nHead
                        best = n
                    }
                }
                if (best >= 0 && myHead - bestHead >= 2) dest = best
            }

            if (dest >= 0) {
                waterOut[i] += 1
                waterIn[dest] += 1
            }
        }
        for (i in 0 until size) {
            s.water[i] = maxOf(0, s.water[i] - waterOut[i])
        }
        for (i in 0 until size) {
            if (waterIn[i] == 0) continue
            if (level.cells[i].terrain.isWaterBody) {
                s.storage[i] += waterIn[i]
                if (level.cells[i].terrain == TerrainType.Reservoir && seen.add(EventKind.RunoffReaches)) {
                    events.add(
                        CausalEvent(beat, EventKind.RunoffReaches, i, "Beat $beat · runoff reached the reservoir")
                    )
                }
            } else {
                s.water[i] = minOf(3, s.water[i] + waterIn[i])
            }
        }

        // A reservoir only holds so much; anything beyond capacity runs over the lip.
        val reservoirCells = level.reservoirCells
        if (reservoirCells.isNotEmpty()) {
            var total = 0
            for (i in reservoirCells) total += s.storage[i]
            if (total > level.reservoirCapacity) {
                var excess = total - level.reservoirCapacity
                s.overflowed = true
                if (seen.add(EventKind.Overflow)) {
                    events.add(
                        CausalEvent(beat, EventKind.Overflow, reservoirCells[0], "Beat $beat · the reservoir brimmed over")
                    )
                }
                for (k in reservoirCells.indices.reversed()) {
                    val i = reservoirCells[k]
                    val take = minOf(excess, s.storage[i])
                    s.storage[i] -= take
                    excess -= take
                    if (excess == 0) break
                }
            }
        }

        // Step 8 — environmental reactions --------------------------------------
        for (i in 0 until size) {
            val cell = level.cells[i]

            if (cell.feature == Feature.Flower) {
                val warmEnough = s.temp[i] in level.bloomTempRange
                if (s.water[i] >= 1 && warmEnough) {
                    s.bloomTimer[i] = s.bloomTimer[i] + 1
                } else if (s.bloomTimer[i] > 0 && s.bloom[i] < 2) {
                    s.bloomTimer[i] = s.bloomTimer[i] - 1
                }
                if (s.bloomTimer[i] >= 2 && s.bloom[i] < 2) {
                    s.bloom[i] = 2
                    if (seen.add(EventKind.FlowerBloomed)) {
                        events.add(CausalEvent(beat, EventKind.FlowerBloomed, i, "Beat $beat · a flower opened"))
                    }
                } else if (s.bloomTimer[i] >= 1 && s.bloom[i] < 1) {
                    s.bloom[i] = 1
                }
            }

            if (cell.terrain == TerrainType.Crop) {
                if (s.temp[i] <= -1) s.freeze[i] = s.freeze[i] + 1
                else if (s.freeze[i] > 0) s.freeze[i] = s.freeze[i] - 1
                if (s.freeze[i] >= 4 && s.frozen[i] == 0) {
                    s.frozen[i] = 1
                    events.add(CausalEvent(beat, EventKind.CropFrozen, i, "Beat $beat · a crop field froze"))
                }
            }

            if (cell.feature == Feature.Windmill) {
                val spin = s.windStr[i] >= 1
                s.spinning[i] = if (spin) 1 else 0
                if (spin) {
                    s.windmillTicks[i] = s.windmillTicks[i] + 1
                    if (seen.add(EventKind.WindmillTurning)) {
                        events.add(
                            CausalEvent(beat, EventKind.WindmillTurning, i, "Beat $beat · a windmill started turning")
                        )
                    }
                }
            }

            if (s.fog[i] > 0) {
                val cleared = s.windStr[i] >= 2 || (s.windStr[i] == 1 && beat % 2 == 0) ||
                    (s.temp[i] >= 1 && beat % 3 == 0)
                if (cleared) s.fog[i] = s.fog[i] - 1
            }

            val floodable = cell.terrain == TerrainType.Village || cell.terrain == TerrainType.Crop
            if (floodable && s.water[i] >= 3 && seen.add(EventKind.Flooded)) {
                events.add(CausalEvent(beat, EventKind.Flooded, i, "Beat $beat · water pooled in the lowlands"))
            }

        }

        val villageFog = level.cells.indices
            .filter { level.cells[it].terrain == TerrainType.Village }
            .sumOf { s.fog[it] }
        if (villageFog == 0 && level.startFogTotal > 0 && seen.add(EventKind.FogCleared)) {
            events.add(CausalEvent(beat, EventKind.FogCleared, 0, "Beat $beat · the village fog lifted"))
        }

        // Step 9 — melt, evaporate, dissipate -----------------------------------
        for (i in 0 until size) {
            if (s.snow[i] > 0 && s.temp[i] >= 1 && beat % 3 == 0) {
                s.snow[i] = s.snow[i] - 1
                s.water[i] = minOf(3, s.water[i] + 1)
            }
            val cell = level.cells[i]
            if (!cell.terrain.isChannel && s.water[i] > 0 && s.temp[i] >= 1 && beat % 4 == 0) {
                s.water[i] = s.water[i] - 1
                s.moisture[i] = minOf(3, s.moisture[i] + 1)
            }
            if (s.cloud[i] > 0 && s.moisture[i] == 0 && beat % 5 == 0) {
                s.cloud[i] = s.cloud[i] - 1
            }
        }

        return s
    }

    // ---------------------------------------------------------------- feedback

    /** Turns the end state into the plain-language causal summary shown on the result sheet. */
    fun explain(level: Level, result: SimResult): List<String> {
        val lines = ArrayList<String>(2)
        val events = result.events
        val rain = events.firstOrNull { it.kind == EventKind.RainBegins }
        val snow = events.firstOrNull { it.kind == EventKind.SnowBegins }
        val collide = events.firstOrNull { it.kind == EventKind.CloudsCollide }
        val runoff = events.firstOrNull { it.kind == EventKind.RunoffReaches }

        val chain = StringBuilder()
        if (collide != null) chain.append("Your fronts met on beat ${collide.beat}")
        if (rain != null) {
            if (chain.isNotEmpty()) chain.append(" — rain started on beat ${rain.beat}")
            else chain.append("Rain started on beat ${rain.beat}")
        }
        if (snow != null) {
            if (chain.isNotEmpty()) chain.append(", and snow settled on beat ${snow.beat}")
            else chain.append("Snow settled on beat ${snow.beat}")
        }
        if (runoff != null) chain.append(", and the runoff reached the reservoir on beat ${runoff.beat}")
        if (chain.isEmpty()) chain.append("Nothing condensed. No cloud ever gathered enough moisture to fall")
        lines.add(chain.append(".").toString())

        if (result.solved) {
            val safe = ArrayList<String>()
            if (!result.finalState.overflowed &&
                level.objectives.any { it.metric == Metric.ReservoirWater }
            ) safe.add("nothing overflowed")
            if (level.cells.any { it.terrain == TerrainType.Crop } &&
                Objectives.measure(
                    ObjectiveSpec(Metric.FrozenCrops, Cmp.Lte, 0, ""), level, result.finalState
                ) == 0
            ) safe.add("every crop stayed warm")
            if (safe.isNotEmpty()) {
                lines.add(safe.joinToString(" and ").replaceFirstChar { it.uppercase() } + ".")
            }
        } else {
            lines.add(failureReason(level, result))
        }
        return lines
    }

    private fun failureReason(level: Level, result: SimResult): String {
        val state = result.finalState
        val missed = result.progress.firstOrNull { !it.met } ?: return "Something is still not quite right."
        val spec = missed.spec
        return when (spec.metric) {
            Metric.ReservoirWater -> when {
                state.overflowed -> "The reservoir overflowed — it took on more water than it could hold."
                missed.current == 0 -> "No water ever reached the reservoir. Check where the rain lands and which way the ground falls."
                missed.current < spec.target -> "Only ${missed.current} of ${spec.target} units of runoff arrived."
                else -> "The reservoir filled past ${spec.target}. Less rain, or rain further from the basin."
            }

            Metric.BloomedFlowers -> if (missed.current == 0)
                "No flower got both water and warmth for two beats running."
            else "Only ${missed.current} of ${spec.target} flowers received enough water in the warm window."

            Metric.FrozenCrops -> {
                val e = result.events.firstOrNull { it.kind == EventKind.CropFrozen }
                if (e != null) "Cold air sat over a crop field until beat ${e.beat} and it froze."
                else "A crop field spent too long below freezing."
            }

            Metric.SnowTiles -> if (missed.current == 0)
                "Precipitation never fell anywhere cold enough to become snow."
            else "Only ${missed.current} summit tiles took snow — ${spec.target} were needed."

            Metric.VillageFog -> "Fog still lingers over the village. Stronger wind, or warmer air, would lift it."

            Metric.WindmillTicks -> "The windmills turned for ${missed.current} beats — ${spec.target} were needed."

            Metric.FloodedTiles -> "Water pooled ${missed.current} tiles deep in the lowlands."

            Metric.WetlandWater -> "The wetland holds ${missed.current} units; ${spec.target} was the mark."
        }
    }
}
