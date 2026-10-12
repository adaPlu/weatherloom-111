package com.rork.weatherloom.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.core.sim.TerrainType

/**
 * One merged outline per biome, in grid units (a cell is 1x1, the board starts at 0,0).
 *
 * The board is felt *appliqué*: a meadow is one piece of wool cut to shape, not a mosaic of
 * identical squares. Merging each biome into a single region means the wool photograph flows
 * continuously across every cell it covers, and an edge is only ever drawn where the material
 * genuinely changes.
 */
class BoardRegions(
    val outlines: Map<TerrainType, Path>,
    val elevations: Map<Int, Path>
)

/**
 * Merges every cell matching [match] into one outline, in grid units. Horizontally contiguous
 * cells become strips first, so a board costs a handful of path ops instead of one per cell.
 * Merging matters for more than speed: overlapping translucent rectangles double-blend along
 * their shared edges and redraw exactly the grid this art direction is trying to escape.
 */
private fun mergeCells(level: Level, match: (Int) -> Boolean): Path? {
    var merged: Path? = null
    for (row in 0 until level.height) {
        var runStart = -1
        for (col in 0..level.width) {
            val inRun = col < level.width && match(row * level.width + col)
            if (inRun && runStart < 0) runStart = col
            if (!inRun && runStart >= 0) {
                val strip = Path().apply {
                    addRect(
                        Rect(
                            runStart.toFloat(),
                            row.toFloat(),
                            col.toFloat(),
                            (row + 1).toFloat()
                        )
                    )
                }
                val current = merged
                merged = if (current == null) strip else {
                    Path().apply { op(current, strip, PathOperation.Union) }
                }
                runStart = -1
            }
        }
    }
    return merged
}

@Composable
fun rememberBoardRegions(level: Level): BoardRegions = remember(level.id) {
    val outlines = HashMap<TerrainType, Path>()
    for (terrain in TerrainType.entries) {
        mergeCells(level) { level.cells[it].terrain == terrain }?.let { outlines[terrain] = it }
    }
    val elevations = HashMap<Int, Path>()
    for (step in level.cells.map { it.elevation }.distinct().filter { it > 0 }) {
        mergeCells(level) { level.cells[it].elevation == step }?.let { elevations[step] = it }
    }
    BoardRegions(outlines, elevations)
}

/** The seam where two pieces of wool meet, stitched down by hand. */
private val StitchInk = Color(0xFF4A3B2A)

/**
 * Lays the wool. Every biome is filled with its own photographed swatch as one continuous
 * sheet, then edged so the boundary reads as a stitched appliqué seam instead of a pixel step.
 */
fun DrawScope.drawWoolGround(
    geo: BoardGeometry,
    art: BoardArt,
    regions: BoardRegions,
    stitch: Boolean = true
) {
    withTransform({
        translate(geo.origin.x, geo.origin.y)
        scale(geo.cell, geo.cell, Offset.Zero)
    }) {
        for ((terrain, outline) in regions.outlines) {
            val brush = art.brush(terrain)
            if (brush != null) {
                drawPath(outline, brush)
            } else {
                drawPath(outline, art.fallbackColor(terrain))
            }
        }
        if (stitch) {
            for ((_, outline) in regions.outlines) {
                drawPath(
                    path = outline,
                    color = StitchInk.copy(alpha = 0.16f),
                    style = Stroke(width = 0.05f)
                )
            }
        }
    }
}

/** Higher ground catches more light. Merged, so no seam shows inside a single plateau. */
fun DrawScope.drawElevationLight(geo: BoardGeometry, regions: BoardRegions) {
    if (regions.elevations.isEmpty()) return
    withTransform({
        translate(geo.origin.x, geo.origin.y)
        scale(geo.cell, geo.cell, Offset.Zero)
    }) {
        for ((step, path) in regions.elevations) {
            drawPath(path, Color.White.copy(alpha = step * 0.05f))
        }
    }
}
