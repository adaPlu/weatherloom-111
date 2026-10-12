package com.rork.weatherloom.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.rork.weatherloom.core.sim.ThreadType
import com.rork.weatherloom.core.sim.WeatherThread
import com.rork.weatherloom.ui.theme.Loom
import kotlin.math.atan2
import kotlin.math.hypot

/** Ribbon colours. Shape carries the same meaning as colour, for colour-blind readability. */
fun ThreadType.ribbonColor(): Color = when (this) {
    ThreadType.WarmFront -> Loom.Coral
    ThreadType.ColdFront -> Loom.Cold
    ThreadType.WindBand -> Loom.WindCream
    ThreadType.MoistureRibbon -> Loom.Moisture
}

fun ThreadType.motifColor(): Color = when (this) {
    ThreadType.WarmFront -> Color(0xFFFFF3EC)
    ThreadType.ColdFront -> Color(0xFFF1F8FB)
    ThreadType.WindBand -> Loom.WindInk
    ThreadType.MoistureRibbon -> Color(0xFFEAF6F4)
}

/** Draws one woven front: a stitched ribbon carrying its motif along the path. */
fun DrawScope.drawThread(
    type: ThreadType,
    points: List<Offset>,
    cellSize: Float,
    alpha: Float = 1f,
    glow: Float = 0f,
    draft: Boolean = false
) {
    if (points.size < 2) {
        if (points.size == 1) {
            drawCircle(type.ribbonColor().copy(alpha = alpha * 0.7f), cellSize * 0.16f, points[0])
        }
        return
    }

    val path = smoothPath(points)
    val width = cellSize * 0.34f

    if (glow > 0f) {
        drawPath(
            path = path,
            color = type.ribbonColor().copy(alpha = 0.25f * glow),
            style = Stroke(width = width * 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // backing thread
    drawPath(
        path = path,
        color = type.ribbonColor().copy(alpha = alpha * if (draft) 0.55f else 0.95f),
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = if (draft) PathEffect.dashPathEffect(
                floatArrayOf(cellSize * 0.22f, cellSize * 0.16f), 0f
            ) else null
        )
    )
    // stitched highlight running along the top edge
    drawPath(
        path = path,
        color = Color.White.copy(alpha = alpha * 0.32f),
        style = Stroke(
            width = width * 0.22f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(cellSize * 0.14f, cellSize * 0.12f), 0f
            )
        )
    )

    drawMotifs(type, points, cellSize, alpha)
}

private fun DrawScope.drawMotifs(
    type: ThreadType,
    points: List<Offset>,
    cellSize: Float,
    alpha: Float
) {
    val spacing = cellSize * 0.62f
    var carried = spacing * 0.5f
    val motif = type.motifColor().copy(alpha = alpha)
    val size = cellSize * 0.13f

    for (k in 0 until points.size - 1) {
        val a = points[k]
        val b = points[k + 1]
        val seg = hypot(b.x - a.x, b.y - a.y)
        if (seg <= 0.001f) continue
        var travelled = carried
        while (travelled < seg) {
            val t = travelled / seg
            val p = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
            rotate(angle, p) {
                when (type) {
                    // Warm: rounded domes on the leading edge.
                    ThreadType.WarmFront -> drawCircle(motif, size * 0.62f, Offset(p.x, p.y - size * 0.5f))
                    // Cold: triangular teeth.
                    ThreadType.ColdFront -> {
                        val tri = Path().apply {
                            moveTo(p.x - size * 0.6f, p.y)
                            lineTo(p.x, p.y - size * 1.15f)
                            lineTo(p.x + size * 0.6f, p.y)
                            close()
                        }
                        drawPath(tri, motif)
                    }
                    // Wind: directional chevrons.
                    ThreadType.WindBand -> {
                        drawLine(
                            motif,
                            Offset(p.x - size * 0.5f, p.y - size * 0.55f),
                            Offset(p.x + size * 0.35f, p.y),
                            strokeWidth = size * 0.34f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            motif,
                            Offset(p.x - size * 0.5f, p.y + size * 0.55f),
                            Offset(p.x + size * 0.35f, p.y),
                            strokeWidth = size * 0.34f,
                            cap = StrokeCap.Round
                        )
                    }
                    // Moisture: droplets.
                    ThreadType.MoistureRibbon -> {
                        val drop = Path().apply {
                            moveTo(p.x, p.y - size * 0.95f)
                            cubicTo(
                                p.x + size * 0.7f, p.y - size * 0.1f,
                                p.x + size * 0.35f, p.y + size * 0.6f,
                                p.x, p.y + size * 0.6f
                            )
                            cubicTo(
                                p.x - size * 0.35f, p.y + size * 0.6f,
                                p.x - size * 0.7f, p.y - size * 0.1f,
                                p.x, p.y - size * 0.95f
                            )
                            close()
                        }
                        drawPath(drop, motif)
                    }
                }
            }
            travelled += spacing
        }
        carried = travelled - seg
    }
}

/** Quadratic smoothing so a wobbly finger still produces an elegant ribbon. */
fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 1 until points.size - 1) {
        val mid = Offset((points[i].x + points[i + 1].x) / 2f, (points[i].y + points[i + 1].y) / 2f)
        path.quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

fun WeatherThread.toOffsets(geo: BoardGeometry): List<Offset> = points.map { geo.denorm(it) }
