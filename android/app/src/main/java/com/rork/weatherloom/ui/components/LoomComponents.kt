package com.rork.weatherloom.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.core.sim.Cmp
import com.rork.weatherloom.core.sim.ObjectiveProgress
import com.rork.weatherloom.core.sim.ThreadType
import com.rork.weatherloom.data.Rating
import com.rork.weatherloom.ui.theme.Loom

/** Soft cream card used everywhere a surface lifts off the parchment. */
@Composable
fun LoomCard(
    modifier: Modifier = Modifier,
    corner: Int = 22,
    background: Color = Loom.Surface,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(corner.dp),
        color = background,
        border = BorderStroke(1.dp, Loom.Outline),
        shadowElevation = 1.dp
    ) { content() }
}

/** Small rounded stat pill: an icon in a tinted disc, then a label. */
@Composable
fun StatPill(
    icon: ImageVector,
    label: String,
    tint: Color = Loom.Moisture,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Loom.Surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Loom.Outline)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = Loom.Ink)
        }
    }
}

/** Live objective readout on the puzzle screen. Turns green the moment it is satisfied. */
@Composable
fun ObjectiveChip(progress: ObjectiveProgress, modifier: Modifier = Modifier) {
    val met = progress.met
    val bg by animateColorAsState(
        if (met) Color(0xFFDDE9D5) else Loom.Surface,
        label = "objective-bg"
    )
    val border by animateColorAsState(
        if (met) Color(0xFF7FA06B) else Loom.Outline,
        label = "objective-border"
    )
    val shown = when (progress.spec.cmp) {
        Cmp.Lte -> if (progress.spec.target == 0) {
            if (progress.current == 0) "clear" else "${progress.current}"
        } else "${progress.current}/${progress.spec.target}"

        Cmp.Gte -> "${minOf(progress.current, progress.spec.target)}/${progress.spec.target}"
        Cmp.Eq -> "${progress.current}/${progress.spec.target}"
    }
    Surface(
        modifier = modifier.semantics {
            contentDescription = "${progress.spec.label} $shown${if (met) ", met" else ""}"
        },
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                progress.spec.label,
                style = MaterialTheme.typography.labelMedium,
                color = Loom.Moss
            )
            Text(
                shown,
                style = MaterialTheme.typography.labelMedium,
                color = if (met) Color(0xFF3F5F3A) else Loom.Ink
            )
        }
    }
}

/** Warm = dome, cold = triangle, wind = chevrons, moisture = droplet. Shape, not just colour. */
@Composable
fun ThreadGlyph(type: ThreadType, tint: Color, size: Int = 16) {
    androidx.compose.foundation.Canvas(Modifier.size(size.dp)) {
        val s = this.size.minDimension
        when (type) {
            ThreadType.WarmFront -> {
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset(s * 0.1f, s * 0.22f),
                    size = androidx.compose.ui.geometry.Size(s * 0.8f, s * 0.8f)
                )
            }

            ThreadType.ColdFront -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.1f, s * 0.78f)
                    lineTo(s * 0.5f, s * 0.16f)
                    lineTo(s * 0.9f, s * 0.78f)
                    close()
                }
                drawPath(p, tint)
            }

            ThreadType.WindBand -> {
                for (k in 0 until 2) {
                    val dx = k * s * 0.3f
                    drawLine(
                        tint,
                        androidx.compose.ui.geometry.Offset(s * 0.12f + dx, s * 0.24f),
                        androidx.compose.ui.geometry.Offset(s * 0.42f + dx, s * 0.5f),
                        strokeWidth = s * 0.12f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        androidx.compose.ui.geometry.Offset(s * 0.12f + dx, s * 0.76f),
                        androidx.compose.ui.geometry.Offset(s * 0.42f + dx, s * 0.5f),
                        strokeWidth = s * 0.12f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            ThreadType.MoistureRibbon -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.5f, s * 0.1f)
                    cubicTo(s * 0.95f, s * 0.5f, s * 0.8f, s * 0.9f, s * 0.5f, s * 0.9f)
                    cubicTo(s * 0.2f, s * 0.9f, s * 0.05f, s * 0.5f, s * 0.5f, s * 0.1f)
                    close()
                }
                drawPath(p, tint)
            }
        }
    }
}

/** Three medallions: the one you earned glows, the others stay pressed tin. */
@Composable
fun RatingMedallions(rating: Rating, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)
    ) {
        Medallion("Seedling", Rating.Seedling, rating)
        Medallion("Bloom", Rating.Bloom, rating)
        Medallion("Flourish", Rating.Flourish, rating)
    }
}

@Composable
private fun Medallion(label: String, tier: Rating, earned: Rating) {
    val isEarned = earned.ordinal >= tier.ordinal && earned != Rating.None
    val isExact = earned == tier
    val scale by animateFloatAsState(
        if (isExact) 1f else 0.9f,
        spring(dampingRatio = 0.55f),
        label = "medallion"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(62.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isEarned) Color(0xFFF6E7C4) else Color(0xFFE7E1D3))
                .border(
                    width = if (isExact) 3.dp else 1.dp,
                    color = if (isExact) Color(0xFFE0B75C) else Loom.Outline,
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            MedallionMark(tier, isEarned)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isExact) Loom.Ink else Loom.Moss
        )
    }
}

@Composable
private fun MedallionMark(tier: Rating, earned: Boolean) {
    val c = if (earned) Color(0xFF9E86C8) else Color(0xFFBEB7A6)
    androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
        val s = size.minDimension
        when (tier) {
            Rating.Seedling -> {
                drawLine(
                    c,
                    androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.9f),
                    androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.42f),
                    strokeWidth = s * 0.09f
                )
                drawOval(
                    c,
                    androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.28f),
                    androidx.compose.ui.geometry.Size(s * 0.42f, s * 0.26f)
                )
            }

            Rating.Bloom -> {
                for (k in 0 until 5) {
                    val a = k * 72.0 * Math.PI / 180.0
                    drawCircle(
                        c,
                        s * 0.17f,
                        androidx.compose.ui.geometry.Offset(
                            s * 0.5f + (Math.cos(a) * s * 0.2f).toFloat(),
                            s * 0.5f + (Math.sin(a) * s * 0.2f).toFloat()
                        )
                    )
                }
                drawCircle(
                    if (earned) Color(0xFFF3D98C) else Color(0xFFD6D0C0),
                    s * 0.13f,
                    androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.5f)
                )
            }

            else -> {
                for (k in 0 until 3) {
                    val x = s * (0.26f + k * 0.24f)
                    drawLine(
                        c,
                        androidx.compose.ui.geometry.Offset(x, s * 0.88f),
                        androidx.compose.ui.geometry.Offset(x, s * 0.4f),
                        strokeWidth = s * 0.07f
                    )
                    drawCircle(c, s * 0.1f, androidx.compose.ui.geometry.Offset(x, s * 0.32f))
                }
            }
        }
    }
}

/** A springy, tactile primary action. */
@Composable
fun LoomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = Loom.Coral,
    contentColor: Color = Loom.Surface
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "press"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.scale(scale),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) container else Color(0xFFE6DFD1),
        contentColor = if (enabled) contentColor else Loom.Moss,
        interactionSource = interaction,
        shadowElevation = if (enabled) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Loom.Ink)
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Loom.Moss)
        }
    }
}
