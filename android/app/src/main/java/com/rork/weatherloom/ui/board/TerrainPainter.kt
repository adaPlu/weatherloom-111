package com.rork.weatherloom.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.core.sim.Feature
import com.rork.weatherloom.core.sim.SimState
import com.rork.weatherloom.core.sim.TerrainType
import com.rork.weatherloom.ui.theme.TerrainPalette
import kotlin.math.roundToInt
import kotlin.math.sin

private fun TerrainType.baseColor(): Color = when (this) {
    TerrainType.Meadow -> TerrainPalette.Meadow
    TerrainType.Crop -> TerrainPalette.Crop
    TerrainType.Village -> TerrainPalette.Village
    TerrainType.Reservoir -> TerrainPalette.Reservoir
    TerrainType.River -> TerrainPalette.River
    TerrainType.Lake -> TerrainPalette.Lake
    TerrainType.Wetland -> TerrainPalette.Wetland
    TerrainType.Forest -> TerrainPalette.Forest
    TerrainType.Mountain -> TerrainPalette.Mountain
    TerrainType.Stone -> TerrainPalette.Stone
    TerrainType.Road -> TerrainPalette.Road
    TerrainType.BareSoil -> TerrainPalette.BareSoil
}

/** Stands a felted prop on the ground, anchored by its feet so it can rise out of its cell. */
fun DrawScope.drawFeltProp(
    img: ImageBitmap,
    centerX: Float,
    baseY: Float,
    span: Float,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null
) {
    val s = span.roundToInt().coerceAtLeast(1)
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset((centerX - span / 2f).roundToInt(), (baseY - span).roundToInt()),
        dstSize = IntSize(s, s),
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = FilterQuality.Medium
    )
}

/** Paints the felt landscape: wool ground, elevation steps, and every handmade prop on it. */
fun DrawScope.drawTerrain(
    level: Level,
    geo: BoardGeometry,
    state: SimState,
    phase: Float,
    art: BoardArt? = null,
    regions: BoardRegions? = null
) {
    val c = geo.cell

    // The wool itself: one continuous hand-cut piece per biome.
    if (art != null && regions != null) {
        drawWoolGround(geo, art, regions)
    } else {
        for (i in level.cells.indices) {
            val r = geo.rect(i)
            drawRect(
                color = level.cells[i].terrain.baseColor(),
                topLeft = Offset(r.left - 0.5f, r.top - 0.5f),
                size = Size(c + 1f, c + 1f)
            )
        }
    }

    // Relief on top of the cloth: lit high ground, shaded drops below each step.
    if (regions != null) drawElevationLight(geo, regions)

    for (i in level.cells.indices) {
        val cell = level.cells[i]
        val r = geo.rect(i)
        val southIndex = i + geo.cols
        if (southIndex < level.cells.size && level.cells[southIndex].elevation < cell.elevation) {
            drawRect(
                color = Color(0xFF2B2118).copy(alpha = 0.2f),
                topLeft = Offset(r.left, r.bottom - c * 0.14f),
                size = Size(c, c * 0.14f)
            )
        }
        val eastIndex = i + 1
        if (i % geo.cols < geo.cols - 1 && level.cells[eastIndex].elevation < cell.elevation) {
            drawRect(
                color = Color(0xFF2B2118).copy(alpha = 0.12f),
                topLeft = Offset(r.right - c * 0.1f, r.top),
                size = Size(c * 0.1f, c)
            )
        }
    }

    // Props last and in row order, so a peak in the row above is overlapped by the row below.
    for (i in level.cells.indices) {
        val cell = level.cells[i]
        val r = geo.rect(i)
        val jitterX = (cellRand(i, 3) - 0.5f) * c * 0.1f

        when (cell.terrain) {
            TerrainType.Forest -> art?.sprite(LoomSprite.Trees)?.let {
                drawFeltProp(it, r.center.x + jitterX, r.bottom + c * 0.1f, c * 1.02f)
            }

            TerrainType.Mountain -> {
                val snowy = state.snow[i] > 0
                val sprite = if (snowy) LoomSprite.PeakSnow else LoomSprite.Peak
                art?.sprite(sprite)?.let {
                    drawFeltProp(it, r.center.x + jitterX * 0.6f, r.bottom + c * 0.12f, c * 1.22f)
                }
            }

            TerrainType.Wetland -> art?.sprite(LoomSprite.Reeds)?.let {
                val sway = sin(phase * 1.6f + i) * c * 0.02f
                drawFeltProp(it, r.center.x + jitterX + sway, r.bottom + c * 0.02f, c * 0.82f)
            }

            TerrainType.River, TerrainType.Reservoir, TerrainType.Lake ->
                drawWaterSheen(i, r, c, phase)

            else -> Unit
        }

        if (cell.terrain == TerrainType.Crop && state.frozen[i] == 1) {
            drawRect(
                color = Color(0xFFDCE9EF).copy(alpha = 0.5f),
                topLeft = Offset(r.left, r.top),
                size = Size(c, c)
            )
        }

        when (cell.feature) {
            Feature.House -> art?.sprite(LoomSprite.Cottage)?.let {
                drawFeltProp(it, r.center.x + jitterX * 0.5f, r.bottom + c * 0.04f, c * 0.92f)
            }

            Feature.Windmill -> drawWindmill(art, r, c, phase, state.spinning[i] == 1)
            Feature.Flower -> {
                val sprite = when (state.bloom[i]) {
                    0 -> LoomSprite.Bud
                    1 -> LoomSprite.FlowerOpen
                    else -> LoomSprite.FlowerBloom
                }
                art?.sprite(sprite)?.let {
                    val breath = sin(phase * 1.2f + i) * c * 0.012f
                    drawFeltProp(it, r.center.x + jitterX * 0.4f, r.bottom + breath, c * 0.8f)
                }
            }

            Feature.None -> Unit
        }
    }
}

private fun DrawScope.drawWaterSheen(i: Int, r: Rect, c: Float, phase: Float) {
    for (k in 0 until 2) {
        val y = r.top + c * (0.34f + k * 0.3f)
        val off = sin(phase * 1.6f + i * 0.7f + k) * c * 0.07f
        drawLine(
            color = Color(0xFFE8F6FA).copy(alpha = 0.32f),
            start = Offset(r.left + c * 0.18f + off, y),
            end = Offset(r.right - c * 0.22f + off, y),
            strokeWidth = c * 0.03f
        )
    }
}

private fun DrawScope.drawWindmill(
    art: BoardArt?,
    r: Rect,
    c: Float,
    phase: Float,
    spinning: Boolean
) {
    val cx = r.center.x
    val tower = art?.sprite(LoomSprite.MillTower)
    val blades = art?.sprite(LoomSprite.MillBlades)
    if (tower != null) drawFeltProp(tower, cx, r.bottom + c * 0.06f, c * 1.0f)
    val hubY = r.bottom + c * 0.06f - c * 0.62f
    if (blades != null) {
        val angle = if (spinning) phase * 90f else 16f
        rotate(degrees = angle, pivot = Offset(cx, hubY)) {
            drawFeltProp(
                blades,
                cx,
                hubY + c * 0.42f,
                c * 0.84f,
                alpha = if (spinning) 1f else 0.88f
            )
        }
    }
}
