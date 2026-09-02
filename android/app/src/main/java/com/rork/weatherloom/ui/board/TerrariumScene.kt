package com.rork.weatherloom.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where a plant sits on the mossy hill inside the photographed cloche, in coordinates
 * normalised to that photograph so the planting bed tracks the image at any crop.
 */
private data class PlantingSlot(val x: Float, val y: Float, val scale: Float)

/**
 * Nine places to grow something, arranged back-to-front around the bowl in the moss.
 * Back slots sit higher and smaller so the bed reads with depth.
 */
private val PLANTING_SLOTS: List<PlantingSlot> = listOf(
    PlantingSlot(0.325f, 0.578f, 0.80f),
    PlantingSlot(0.470f, 0.570f, 0.80f),
    PlantingSlot(0.615f, 0.578f, 0.80f),
    PlantingSlot(0.265f, 0.618f, 0.92f),
    PlantingSlot(0.400f, 0.610f, 0.92f),
    PlantingSlot(0.540f, 0.610f, 0.92f),
    PlantingSlot(0.680f, 0.620f, 0.92f),
    PlantingSlot(0.345f, 0.655f, 1.04f),
    PlantingSlot(0.575f, 0.655f, 1.04f)
)

/** How wide one planted species reads, as a fraction of the cloche photograph. */
private const val SPECIMEN_SPAN = 0.17f

/**
 * The player's keepsake: a real needle-felted cloche, photographed once, that grows a
 * new felted species in its moss bed for every biome they finish.
 */
@Composable
fun TerrariumScene(
    unlocked: List<String>,
    phase: Float,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    val cloche = ImageBitmap.imageResource(R.drawable.terrarium_cloche)
    val specimens = rememberSpecimenArt(unlocked)

    Canvas(modifier) {
        // The parchment wall behind the photograph, so any letterboxing still belongs.
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFF8F1E4), Color(0xFFF0E5D1))),
            size = size
        )

        // Cover-fit the photograph; the Canvas clips whatever overflows.
        val scale = maxOf(size.width / cloche.width, size.height / cloche.height)
        val dw = cloche.width * scale
        val dh = cloche.height * scale
        val dx = (size.width - dw) / 2f
        val dy = (size.height - dh) / 2f
        drawImage(
            image = cloche,
            dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt())
        )

        drawPlantedSpecies(unlocked, specimens, dx, dy, dw, dh, phase, reducedMotion)
        drawGlassSheen(dx, dy, dw, dh)
    }
}

/**
 * Plants each unlocked species into its slot, painting back rows first so the front
 * of the bed correctly overlaps the back.
 */
private fun DrawScope.drawPlantedSpecies(
    unlocked: List<String>,
    specimens: Map<String, ImageBitmap>,
    dx: Float,
    dy: Float,
    dw: Float,
    dh: Float,
    phase: Float,
    reducedMotion: Boolean
) {
    unlocked.take(PLANTING_SLOTS.size)
        .mapIndexed { index, id -> index to id }
        .sortedBy { PLANTING_SLOTS[it.first].y }
        .forEach { (index, id) ->
            val slot = PLANTING_SLOTS[index]
            val span = dw * SPECIMEN_SPAN * slot.scale
            val sway = if (reducedMotion) 0f else sin(phase * 1.1f + index * 0.7f) * span * 0.022f
            val cx = dx + dw * slot.x + sway
            val groundY = dy + dh * slot.y
            val art = specimens[id]
            if (art != null) {
                drawImage(
                    image = art,
                    dstOffset = IntOffset(
                        (cx - span / 2f).roundToInt(),
                        (groundY - span).roundToInt()
                    ),
                    dstSize = IntSize(span.roundToInt(), span.roundToInt())
                )
            } else {
                // A species with no photograph yet still shows up, hand-stitched.
                drawSpecimen(id, Offset(cx, groundY), span * 0.3f, sway, phase)
            }
        }
}

/** A whisper of curved glass over the planted bed, so the species read as enclosed. */
private fun DrawScope.drawGlassSheen(dx: Float, dy: Float, dw: Float, dh: Float) {
    val left = dx + dw * 0.16f
    val right = dx + dw * 0.84f
    val bottom = dy + dh * 0.788f
    val shoulder = dy + dh * 0.40f
    val top = dy + dh * 0.215f

    val dome = Path().apply {
        moveTo(left, bottom)
        lineTo(left, shoulder)
        cubicTo(left, top, right, top, right, shoulder)
        lineTo(right, bottom)
        close()
    }

    clipPath(dome) {
        drawRect(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.02f),
                    Color(0xFFBFD8DE).copy(alpha = 0.10f)
                ),
                startY = top,
                endY = bottom
            ),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top)
        )
        val streak = Path().apply {
            moveTo(dx + dw * 0.30f, dy + dh * 0.66f)
            cubicTo(
                dx + dw * 0.24f, dy + dh * 0.50f,
                dx + dw * 0.27f, dy + dh * 0.34f,
                dx + dw * 0.36f, dy + dh * 0.27f
            )
        }
        drawPath(
            streak,
            Color.White.copy(alpha = 0.22f),
            style = Stroke(width = dw * 0.03f)
        )
    }
}

/** Each collectible is a tiny hand-stitched plant with its own silhouette. */
fun DrawScope.drawSpecimen(id: String, at: Offset, s: Float, sway: Float, phase: Float) {
    when (id) {
        "rainbell" -> {
            drawLine(Color(0xFF5E8354), at, Offset(at.x + sway, at.y - s * 1.5f), strokeWidth = s * 0.11f)
            for (k in 0 until 3) {
                val bx = at.x + sway * (1f - k * 0.2f) + (k - 1) * s * 0.32f
                val by = at.y - s * (1.5f - k * 0.16f)
                drawCircle(Color(0xFF9E86C8), s * 0.24f, Offset(bx, by))
                drawCircle(Color(0xFFC5B4E0), s * 0.13f, Offset(bx, by + s * 0.12f))
            }
        }

        "frostfern" -> {
            for (k in 0 until 5) {
                val a = (-70f + k * 35f) * Math.PI.toFloat() / 180f
                drawLine(
                    Color(0xFF8FB6B4),
                    at,
                    Offset(at.x + sin(a) * s * 1.2f + sway, at.y - cos(a) * s * 1.2f),
                    strokeWidth = s * 0.1f
                )
            }
            drawCircle(Color(0xFFE4F1F2).copy(alpha = 0.7f), s * 0.2f, Offset(at.x, at.y - s * 0.9f))
        }

        "cloudmoss" -> {
            for (k in 0 until 4) {
                drawCircle(
                    Color(0xFF6F9A5F),
                    s * (0.32f - k * 0.03f),
                    Offset(at.x + (k - 1.5f) * s * 0.34f, at.y - s * (0.18f + (k % 2) * 0.2f))
                )
            }
        }

        "sunlace" -> {
            for (k in 0 until 3) {
                val bx = at.x + (k - 1) * s * 0.4f + sway
                drawLine(Color(0xFF6C9160), Offset(bx, at.y), Offset(bx, at.y - s), strokeWidth = s * 0.09f)
                for (p in 0 until 4) {
                    val a = p * 90.0 * Math.PI / 180.0
                    drawCircle(
                        Color(0xFFF0C651),
                        s * 0.14f,
                        Offset(
                            bx + (cos(a) * s * 0.16f).toFloat(),
                            at.y - s + (sin(a) * s * 0.16f).toFloat()
                        )
                    )
                }
            }
        }

        "mistcap" -> {
            for (k in 0 until 2) {
                val bx = at.x + (k - 0.5f) * s * 0.55f
                val by = at.y - s * (0.5f + k * 0.25f)
                drawLine(Color(0xFFE9E1CE), Offset(bx, at.y), Offset(bx, by), strokeWidth = s * 0.18f)
                drawArc(
                    color = Color(0xFF9E86C8),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(bx - s * 0.38f, by - s * 0.34f),
                    size = Size(s * 0.76f, s * 0.68f)
                )
            }
        }

        "windreed" -> {
            for (k in 0 until 4) {
                val bx = at.x + (k - 1.5f) * s * 0.28f
                val bend = sway * (1f + k * 0.3f) + sin(phase * 1.6f + k) * s * 0.12f
                drawLine(
                    Color(0xFFC8B77E),
                    Offset(bx, at.y),
                    Offset(bx + bend, at.y - s * 1.4f),
                    strokeWidth = s * 0.09f
                )
                drawCircle(Color(0xFFE0D2A2), s * 0.12f, Offset(bx + bend, at.y - s * 1.42f))
            }
        }

        "thawlily" -> {
            // A cupped white bloom sitting in its own meltwater puddle.
            drawOval(
                color = Color(0xFF8FB6C4).copy(alpha = 0.55f),
                topLeft = Offset(at.x - s * 0.6f, at.y - s * 0.12f),
                size = Size(s * 1.2f, s * 0.26f)
            )
            drawLine(
                Color(0xFF6C9160),
                at,
                Offset(at.x + sway, at.y - s * 1.1f),
                strokeWidth = s * 0.1f
            )
            for (k in 0 until 5) {
                val a = (-64f + k * 32f) * Math.PI.toFloat() / 180f
                drawOval(
                    color = if (k % 2 == 0) Color(0xFFFBF6EC) else Color(0xFFE9F1F4),
                    topLeft = Offset(
                        at.x + sway + sin(a) * s * 0.34f - s * 0.15f,
                        at.y - s * 1.1f - cos(a) * s * 0.3f - s * 0.2f
                    ),
                    size = Size(s * 0.3f, s * 0.42f)
                )
            }
            drawCircle(Color(0xFFF0C651), s * 0.13f, Offset(at.x + sway, at.y - s * 1.16f))
        }

        "galeflax" -> {
            // Every fibre lies over in the same direction, like a wind-combed field.
            for (k in 0 until 5) {
                val bx = at.x + (k - 2f) * s * 0.24f
                val lean = s * 0.5f + sway * 1.4f + sin(phase * 1.1f + k * 0.4f) * s * 0.08f
                drawLine(
                    Color(0xFF9FA86F),
                    Offset(bx, at.y),
                    Offset(bx + lean, at.y - s * 1.15f),
                    strokeWidth = s * 0.08f
                )
                drawOval(
                    color = Color(0xFFC9D3E0),
                    topLeft = Offset(bx + lean - s * 0.1f, at.y - s * 1.3f),
                    size = Size(s * 0.26f, s * 0.16f)
                )
            }
        }

        "loomstar" -> {
            // Six petals, each a different weather colour, on a slow shimmer.
            val petals = listOf(
                Color(0xFFE2694F), Color(0xFF4E8FAE), Color(0xFF5FA8A0),
                Color(0xFFC9C3D6), Color(0xFFF0C651), Color(0xFF9E86C8)
            )
            drawLine(
                Color(0xFF6C9160),
                at,
                Offset(at.x + sway, at.y - s * 1.0f),
                strokeWidth = s * 0.1f
            )
            val cx = at.x + sway
            val cy = at.y - s * 1.05f
            petals.forEachIndexed { k, c ->
                val a = (k * 60.0) * Math.PI / 180.0
                val pulse = 1f + sin(phase * 1.3f + k * 0.9f) * 0.08f
                drawCircle(
                    color = c,
                    radius = s * 0.2f * pulse,
                    center = Offset(
                        cx + (cos(a) * s * 0.34f).toFloat(),
                        cy + (sin(a) * s * 0.34f).toFloat()
                    )
                )
            }
            drawCircle(Color(0xFFFBF6EC), s * 0.17f, Offset(cx, cy))
        }
    }
}

/** Small square portrait of one species, used by the almanac and reward cards. */
@Composable
fun SpecimenBadge(id: String, modifier: Modifier = Modifier, phase: Float = 0f) {
    val art = LoomArt.specimenRes(id)
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF2F5EC), Color(0xFFE4EBDB)))),
        contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawSpecimen(
                    id = id,
                    at = Offset(size.width / 2f, size.height * 0.78f),
                    s = size.minDimension * 0.24f,
                    sway = sin(phase) * size.minDimension * 0.015f,
                    phase = phase
                )
            }
        }
    }
}
