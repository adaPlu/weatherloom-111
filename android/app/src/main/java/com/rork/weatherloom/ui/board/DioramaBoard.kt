package com.rork.weatherloom.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.core.sim.Dir
import com.rork.weatherloom.core.sim.SimState
import com.rork.weatherloom.core.sim.ThreadType
import com.rork.weatherloom.core.sim.WeatherThread
import kotlin.math.abs
import kotlin.math.hypot

/** What the player drew, resolved onto the grid and already normalised for storage. */
data class DrawnStroke(
    val points: List<Offset>,
    val normPoints: List<Pair<Float, Float>>,
    val cells: List<Int>,
    val dir: Dir
)

/** Mutable holder so the draw pass can publish geometry without touching Compose state. */
private class GeometryRef {
    var value: BoardGeometry? = null
}

/**
 * The miniature world. Renders the level and its live simulation state, and — when a
 * thread type is armed — lets the player weave a front across it.
 */
@Composable
fun DioramaBoard(
    level: Level,
    state: SimState,
    threads: List<WeatherThread>,
    phase: Float,
    modifier: Modifier = Modifier,
    armedThread: ThreadType? = null,
    highlightCell: Int? = null,
    showTemperature: Boolean = false,
    reducedMotion: Boolean = false,
    glowThreads: Boolean = false,
    onStrokeComplete: ((DrawnStroke) -> Unit)? = null,
    onTapCell: ((Int) -> Unit)? = null
) {
    val weave = rememberFeltWeave()
    val art = rememberBoardArt()
    val regions = rememberBoardRegions(level)
    val geoRef = remember(level.id) { GeometryRef() }
    var draft by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val armed by rememberUpdatedState(armedThread)
    val complete by rememberUpdatedState(onStrokeComplete)
    val maxCells by rememberUpdatedState(level.maxThreadCells)

    Box(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(level.id, onTapCell != null) {
                    if (onTapCell == null) return@pointerInput
                    detectTapGestures { p ->
                        geoRef.value?.cellAt(p)?.let { if (it >= 0) onTapCell(it) }
                    }
                }
                .pointerInput(level.id, armedThread) {
                    if (armed == null || complete == null) return@pointerInput
                    detectDragGestures(
                        onDragStart = { draft = listOf(it) },
                        onDrag = { change, _ ->
                            change.consume()
                            val last = draft.lastOrNull()
                            val g = geoRef.value
                            val minStep = (g?.cell ?: 40f) * 0.18f
                            if (last == null || hypot(
                                    change.position.x - last.x,
                                    change.position.y - last.y
                                ) > minStep
                            ) {
                                draft = draft + change.position
                            }
                        },
                        onDragEnd = {
                            val g = geoRef.value
                            val pts = draft
                            draft = emptyList()
                            if (g != null && pts.size >= 2) {
                                val resolved = resolveStroke(pts, g, maxCells)
                                if (resolved != null) complete?.invoke(resolved)
                            }
                        },
                        onDragCancel = { draft = emptyList() }
                    )
                }
        ) {
            val g = BoardGeometry.fit(size, level.width, level.height)
            geoRef.value = g

            drawMat(g, weave)

            val clip = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = g.origin.x,
                        top = g.origin.y,
                        right = g.origin.x + g.width,
                        bottom = g.origin.y + g.height,
                        cornerRadius = CornerRadius(g.cell * 0.42f)
                    )
                )
            }

            clipPath(clip) {
                drawTerrain(level, g, state, phase, art = art, regions = regions)
                drawWeather(level, g, state, phase, showTemperature, reducedMotion, art)

                highlightCell?.let { hc ->
                    if (hc in 0 until level.size) {
                        val r = g.rect(hc)
                        drawRoundRect(
                            color = Color(0xFFFFF6D8).copy(alpha = 0.75f),
                            topLeft = Offset(r.left + 1f, r.top + 1f),
                            size = Size(g.cell - 2f, g.cell - 2f),
                            cornerRadius = CornerRadius(g.cell * 0.2f),
                            style = Stroke(width = g.cell * 0.08f)
                        )
                    }
                }

                for (t in threads) {
                    drawThread(
                        type = t.type,
                        points = t.toOffsets(g),
                        cellSize = g.cell,
                        glow = if (glowThreads) 1f else 0f
                    )
                }
                if (draft.size >= 2 && armed != null) {
                    drawThread(armed!!, draft, g.cell, alpha = 0.9f, draft = true)
                }
            }

            // stitched felt border around the whole mat
            drawRoundRect(
                color = Color(0xFFE7DCC6),
                topLeft = g.origin,
                size = Size(g.width, g.height),
                cornerRadius = CornerRadius(g.cell * 0.42f),
                style = Stroke(width = g.cell * 0.09f)
            )
        }
    }
}

private fun DrawScope.drawMat(g: BoardGeometry, weave: Brush?) {
    val topLeft = Offset(g.origin.x - 3.dp.toPx(), g.origin.y - 3.dp.toPx())
    val size = Size(g.width + 6.dp.toPx(), g.height + 6.dp.toPx())
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFF3EBDA), Color(0xFFE8DFCB))
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(g.cell * 0.5f)
    )
    if (weave != null) {
        drawRoundRect(
            brush = weave,
            topLeft = topLeft,
            size = size,
            cornerRadius = CornerRadius(g.cell * 0.5f),
            blendMode = BlendMode.Multiply
        )
    }
}

/** Turns a finger path into grid cells, trimming it to the level's stroke budget. */
fun resolveStroke(points: List<Offset>, geo: BoardGeometry, maxCells: Int): DrawnStroke? {
    if (points.size < 2) return null
    val cells = ArrayList<Int>()
    // sample densely along the path so no crossed cell is skipped
    for (k in 0 until points.size - 1) {
        val a = points[k]
        val b = points[k + 1]
        val steps = (hypot(b.x - a.x, b.y - a.y) / (geo.cell * 0.25f)).toInt().coerceAtLeast(1)
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            val p = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            val c = geo.cellAt(p)
            if (c >= 0 && (cells.isEmpty() || cells.last() != c)) cells.add(c)
        }
    }
    val unique = LinkedHashSet(cells).toList()
    if (unique.isEmpty()) return null
    val trimmed = unique.take(maxCells)

    val first = geo.center(trimmed.first())
    val last = geo.center(trimmed.last())
    val dx = last.x - first.x
    val dy = last.y - first.y
    val dir = when {
        trimmed.size == 1 -> Dir.None
        abs(dx) >= abs(dy) -> if (dx > 0) Dir.East else Dir.West
        else -> if (dy > 0) Dir.South else Dir.North
    }
    // snap the rendered ribbon onto the resolved cells so what you see is what runs
    val snapped = trimmed.map { geo.center(it) }
    return DrawnStroke(
        points = snapped,
        normPoints = snapped.map { geo.norm(it) },
        cells = trimmed,
        dir = dir
    )
}

fun DrawnStroke.toThread(type: ThreadType): WeatherThread = WeatherThread(
    type = type,
    points = normPoints,
    cells = cells,
    dir = dir
)
