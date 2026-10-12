package com.rork.weatherloom.ui.puzzle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.R
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.core.sim.CausalEvent
import com.rork.weatherloom.core.sim.EventKind
import com.rork.weatherloom.core.sim.SimResult
import com.rork.weatherloom.core.sim.SimState
import com.rork.weatherloom.ui.board.DioramaBoard
import com.rork.weatherloom.ui.components.ObjectiveChip
import com.rork.weatherloom.ui.theme.Loom

private fun EventKind.tint(): Color = when (this) {
    EventKind.CloudsCollide -> Color(0xFF9E86C8)
    EventKind.RainBegins -> Loom.Cold
    EventKind.SnowBegins -> Color(0xFF8FB6C4)
    EventKind.RunoffReaches -> Loom.Moisture
    EventKind.FlowerBloomed -> Color(0xFFB58BC9)
    EventKind.WindmillTurning -> Loom.WindInk
    EventKind.FogCleared -> Color(0xFFA9A2BE)
    EventKind.CropFrozen -> Loom.Coral
    EventKind.Overflow -> Loom.Coral
    EventKind.Flooded -> Loom.Coral
}

/**
 * Full-screen playback. The timeline is the point: every causal event is marked on it,
 * and dragging the head replays the hollow beat by beat.
 */
@Composable
fun PlaybackScreen(
    level: Level,
    result: SimResult,
    state: SimState,
    ui: PuzzleUiState,
    phase: Float,
    reducedMotion: Boolean,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onSpeed: () -> Unit,
    onRestart: () -> Unit,
    onScrub: (Int) -> Unit,
    onEvent: (CausalEvent) -> Unit
) {
    BackHandler(onBack = onClose)
    val visibleEvents = remember(result, ui.beat) {
        result.events.filter { it.beat <= ui.beat }.takeLast(4)
    }

    Box(Modifier.fillMaxSize().background(Loom.Canvas)) {
        // A real wool sky behind the run, so the weather has weather to happen in.
        Image(
            painter = painterResource(R.drawable.felted_sky),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )
        // Settle the upper chrome and let the sky fade into the parchment sheet below.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF243036).copy(alpha = 0.34f),
                        0.28f to Color.Transparent,
                        0.72f to Loom.Canvas.copy(alpha = 0.55f),
                        1f to Loom.Canvas
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onClose,
                    shape = RoundedCornerShape(16.dp),
                    color = Loom.Surface,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Back to drawing",
                            tint = Loom.Ink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    level.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFF3EFE4)
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(42.dp))
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                DioramaBoard(
                    level = level,
                    state = state,
                    threads = ui.threads,
                    phase = phase,
                    modifier = Modifier.fillMaxSize(),
                    highlightCell = ui.inspectCell,
                    reducedMotion = reducedMotion,
                    glowThreads = true
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visibleEvents.isEmpty()) {
                    EventPill(
                        text = "The sky is still settling",
                        tint = Loom.Moss,
                        onClick = null
                    )
                }
                visibleEvents.forEach { e ->
                    EventPill(text = e.text, tint = e.kind.tint()) { onEvent(e) }
                }
            }

            Surface(
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                color = Loom.Surface
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        "Beat ${ui.beat} / ${result.beats}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Loom.Ink,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(10.dp))
                    BeatTimeline(
                        beat = ui.beat,
                        total = result.beats,
                        events = result.events,
                        onScrub = onScrub
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlButton(
                            modifier = Modifier.weight(1f),
                            content = {
                                Icon(
                                    if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (ui.playing) "Pause" else "Play",
                                    tint = Loom.Coral,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            onClick = onTogglePlay
                        )
                        ControlButton(
                            modifier = Modifier.weight(1f),
                            content = {
                                Text(
                                    "${ui.speed}×",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Loom.Ink
                                )
                            },
                            onClick = onSpeed
                        )
                        ControlButton(
                            modifier = Modifier.weight(1f),
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Replay,
                                        contentDescription = null,
                                        tint = Loom.Ink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "Restart",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Loom.Ink
                                    )
                                }
                            },
                            onClick = onRestart
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ui.progress().forEach { ObjectiveChip(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun EventPill(text: String, tint: Color, onClick: (() -> Unit)?) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(50),
        color = Loom.Surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tint)
            )
            Text(text, style = MaterialTheme.typography.labelSmall, color = Loom.Ink)
        }
    }
}

@Composable
private fun ControlButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(20.dp),
        color = Loom.SurfaceSunk,
        border = BorderStroke(1.dp, Loom.Outline)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Scrubbable beat track with a tick ruler and one dot per causal event. */
@Composable
private fun BeatTimeline(
    beat: Int,
    total: Int,
    events: List<CausalEvent>,
    onScrub: (Int) -> Unit
) {
    val label = "Beat $beat of $total, drag to scrub"

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = label }
            .pointerInput(total) {
                detectTapGestures { p -> onScrub(((p.x / size.width) * total).toInt()) }
            }
            .pointerInput(total) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        onScrub(((change.position.x / size.width) * total).toInt())
                    }
                )
            }
    ) {
        val trackY = size.height * 0.56f
        val w = size.width

        // ruler
        val majorEvery = if (total > 40) 10 else 5
        for (b in 0..total) {
            val x = w * b / total
            val major = b % majorEvery == 0
            drawLine(
                color = if (major) Loom.Moss.copy(alpha = 0.5f) else Loom.Outline,
                start = Offset(x, trackY + 6.dp.toPx()),
                end = Offset(x, trackY + (if (major) 16.dp else 11.dp).toPx()),
                strokeWidth = if (major) 1.6.dp.toPx() else 1.dp.toPx()
            )
        }

        drawLine(
            color = Loom.Outline,
            start = Offset(0f, trackY),
            end = Offset(w, trackY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Loom.Coral,
            start = Offset(0f, trackY),
            end = Offset(w * beat / total.coerceAtLeast(1), trackY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        events.forEach { e ->
            val x = w * e.beat / total.coerceAtLeast(1)
            drawCircle(Loom.Surface, 7.dp.toPx(), Offset(x, trackY))
            drawCircle(e.kind.tint(), 5.dp.toPx(), Offset(x, trackY))
        }

        val hx = w * beat / total.coerceAtLeast(1)
        drawCircle(Loom.Coral, 9.dp.toPx(), Offset(hx, trackY - 14.dp.toPx()))
        drawLine(
            color = Loom.Coral,
            start = Offset(hx, trackY - 14.dp.toPx()),
            end = Offset(hx, trackY + 4.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
    }
}
