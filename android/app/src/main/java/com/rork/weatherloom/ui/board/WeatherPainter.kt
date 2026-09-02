package com.rork.weatherloom.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.core.sim.SimState
import com.rork.weatherloom.core.sim.TerrainType
import kotlin.math.sin

/** Paints everything the atmosphere is doing on top of the felt landscape. */
fun DrawScope.drawWeather(
    level: Level,
    geo: BoardGeometry,
    state: SimState,
    phase: Float,
    showTemperature: Boolean,
    reducedMotion: Boolean,
    art: BoardArt? = null
) {
    val c = geo.cell

    // Standing water and snow sit on the ground, under the sky.
    for (i in level.cells.indices) {
        val r = geo.rect(i)
        val terrain = level.cells[i].terrain
        if (state.water[i] > 0 && !terrain.isWaterBody) {
            val a = 0.16f + state.water[i] * 0.15f
            drawRect(
                color = Color(0xFF5E9CB8).copy(alpha = a),
                topLeft = Offset(r.left, r.top),
                size = Size(c, c)
            )
            val ripple = if (reducedMotion) 0f else sin(phase * 2f + i) * c * 0.05f
            drawLine(
                color = Color(0xFFCDE7F0).copy(alpha = 0.5f),
                start = Offset(r.left + c * 0.2f + ripple, r.center.y),
                end = Offset(r.right - c * 0.2f + ripple, r.center.y),
                strokeWidth = c * 0.03f
            )
        }
        if (state.snow[i] > 0 && terrain != TerrainType.Mountain) {
            drawRect(
                color = Color(0xFFF6F3EA).copy(alpha = 0.4f + state.snow[i] * 0.22f),
                topLeft = Offset(r.left, r.top),
                size = Size(c, c)
            )
        }
        if (showTemperature) {
            val t = state.temp[i]
            if (t != 0) {
                val warm = t > 0
                drawRect(
                    color = if (warm) Color(0xFFE2694F).copy(alpha = 0.06f * t)
                    else Color(0xFF4E8FAE).copy(alpha = 0.07f * -t),
                    topLeft = Offset(r.left, r.top),
                    size = Size(c, c)
                )
            }
        }
    }

    // Fog is a soft lavender veil that clings to the ground.
    for (i in level.cells.indices) {
        if (state.fog[i] <= 0) continue
        val r = geo.rect(i)
        val drift = if (reducedMotion) 0f else sin(phase * 0.8f + i * 0.5f) * c * 0.08f
        val a = 0.26f + state.fog[i] * 0.2f
        for (k in 0 until 3) {
            drawCircle(
                color = Color(0xFFD9D5E4).copy(alpha = a * 0.6f),
                radius = c * (0.34f + cellRand(i, 200 + k) * 0.18f),
                center = Offset(
                    r.left + (0.25f + cellRand(i, 210 + k) * 0.5f) * c + drift,
                    r.top + (0.3f + cellRand(i, 220 + k) * 0.45f) * c
                )
            )
        }
    }

    // Rain and snowfall, drawn before the clouds so the cloud reads as the source.
    for (i in level.cells.indices) {
        val n = state.precip[i]
        if (n <= 0) continue
        val r = geo.rect(i)
        val snowing = state.precipSnow[i] == 1
        val strands = 3 + n * 2
        for (k in 0 until strands) {
            val fx = r.left + (0.1f + cellRand(i, 300 + k) * 0.8f) * c
            val t = if (reducedMotion) cellRand(i, 320 + k)
            else ((phase * 0.9f + cellRand(i, 320 + k)) % 1f)
            val fy = r.top + t * c
            if (snowing) {
                drawCircle(Color(0xFFFBF8F1).copy(alpha = 0.85f), c * 0.035f, Offset(fx, fy))
            } else {
                drawLine(
                    color = Color(0xFF8FBDD3).copy(alpha = 0.8f),
                    start = Offset(fx, fy),
                    end = Offset(fx - c * 0.04f, fy + c * 0.16f),
                    strokeWidth = c * 0.025f
                )
            }
        }
    }

    // Clouds: real carded-wool puffs, heavier and darker as the cell thickens.
    for (i in level.cells.indices) {
        val d = state.cloud[i]
        if (d <= 0) continue
        val r = geo.rect(i)
        val drift = if (reducedMotion) 0f else sin(phase * 0.5f + i * 0.3f) * c * 0.06f
        val puff = art?.sprite(if (d >= 3) LoomSprite.CloudDark else LoomSprite.CloudLight)
        if (puff != null) {
            val span = c * (0.92f + d * 0.1f)
            drawFeltProp(
                img = puff,
                centerX = r.center.x + drift + (cellRand(i, 400) - 0.5f) * c * 0.12f,
                baseY = r.center.y + span * 0.5f,
                span = span,
                alpha = (0.55f + d * 0.15f).coerceAtMost(1f)
            )
        } else {
            val alpha = 0.3f + d * 0.2f
            val body = if (d >= 3) Color(0xFFCFCEC9) else Color(0xFFF7F5EF)
            for (k in 0 until 2 + d) {
                drawCircle(
                    color = body.copy(alpha = alpha),
                    radius = c * (0.2f + cellRand(i, 400 + k) * 0.22f),
                    center = Offset(
                        r.left + (0.18f + cellRand(i, 410 + k) * 0.64f) * c + drift,
                        r.top + (0.18f + cellRand(i, 420 + k) * 0.5f) * c
                    )
                )
            }
        }
    }

    // Wind: pale chevrons riding the airflow.
    for (i in level.cells.indices) {
        val str = state.windStr[i]
        if (str <= 0) continue
        val dir = state.windDir[i]
        if (dir == 0) continue
        val r = geo.rect(i)
        val t = if (reducedMotion) 0.5f else ((phase * 0.7f + cellRand(i, 500)) % 1f)
        val (dx, dy) = when (dir) {
            1 -> 0f to -1f
            2 -> 1f to 0f
            3 -> 0f to 1f
            else -> -1f to 0f
        }
        val px = r.center.x + dx * (t - 0.5f) * c
        val py = r.center.y + dy * (t - 0.5f) * c
        val len = c * 0.14f
        val a = 0.2f + str * 0.14f
        drawLine(
            color = Color(0xFFFFFDF6).copy(alpha = a),
            start = Offset(px - dy * len - dx * len, py - dx * len - dy * len),
            end = Offset(px, py),
            strokeWidth = c * 0.03f
        )
        drawLine(
            color = Color(0xFFFFFDF6).copy(alpha = a),
            start = Offset(px + dy * len - dx * len, py + dx * len - dy * len),
            end = Offset(px, py),
            strokeWidth = c * 0.03f
        )
    }
}
