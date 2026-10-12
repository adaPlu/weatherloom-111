package com.rork.weatherloom.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.floor

/** Maps between board pixels and the logical grid. */
data class BoardGeometry(
    val origin: Offset,
    val cell: Float,
    val cols: Int,
    val rows: Int
) {
    val width: Float get() = cell * cols
    val height: Float get() = cell * rows

    fun rect(i: Int): Rect {
        val x = i % cols
        val y = i / cols
        return Rect(
            origin.x + x * cell,
            origin.y + y * cell,
            origin.x + (x + 1) * cell,
            origin.y + (y + 1) * cell
        )
    }

    fun center(i: Int): Offset {
        val x = i % cols
        val y = i / cols
        return Offset(origin.x + (x + 0.5f) * cell, origin.y + (y + 0.5f) * cell)
    }

    fun cellAt(p: Offset): Int {
        val x = floor((p.x - origin.x) / cell).toInt()
        val y = floor((p.y - origin.y) / cell).toInt()
        if (x !in 0 until cols || y !in 0 until rows) return -1
        return y * cols + x
    }

    /** Clamped normalised coordinates so strokes stay on the mat. */
    fun norm(p: Offset): Pair<Float, Float> = Pair(
        ((p.x - origin.x) / width).coerceIn(0f, 1f),
        ((p.y - origin.y) / height).coerceIn(0f, 1f)
    )

    fun denorm(n: Pair<Float, Float>): Offset =
        Offset(origin.x + n.first * width, origin.y + n.second * height)

    companion object {
        fun fit(size: Size, cols: Int, rows: Int, inset: Float = 0f): BoardGeometry {
            val avail = Size(size.width - inset * 2, size.height - inset * 2)
            val cell = minOf(avail.width / cols, avail.height / rows)
            val w = cell * cols
            val h = cell * rows
            return BoardGeometry(
                origin = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                cell = cell,
                cols = cols,
                rows = rows
            )
        }
    }
}

/** Stable pseudo-random so the handcrafted jitter never flickers between frames. */
fun cellHash(i: Int, salt: Int): Int {
    var h = i * 374761393 + salt * 668265263
    h = (h xor (h shr 13)) * 1274126177
    return h xor (h shr 16)
}

fun cellRand(i: Int, salt: Int): Float = ((cellHash(i, salt) and 0x7fffffff) % 1000) / 1000f
