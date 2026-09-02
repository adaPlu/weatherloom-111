package com.rork.weatherloom.ui.puzzle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.audio.LoomAudio
import com.rork.weatherloom.audio.Sfx
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.core.sim.SimState
import com.rork.weatherloom.core.sim.ThreadType
import com.rork.weatherloom.ui.board.DioramaBoard
import com.rork.weatherloom.ui.board.DrawnStroke
import com.rork.weatherloom.ui.board.ribbonColor
import com.rork.weatherloom.ui.components.LoomButton
import com.rork.weatherloom.ui.components.LoomCard
import com.rork.weatherloom.ui.components.ObjectiveChip
import com.rork.weatherloom.ui.components.ThreadGlyph
import com.rork.weatherloom.ui.theme.Loom

/** The signature screen: inspect the hollow, weave your fronts, then run the loom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleDrawScreen(
    level: Level,
    state: SimState,
    ui: PuzzleUiState,
    phase: Float,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onArm: (ThreadType) -> Unit,
    onStroke: (DrawnStroke) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onHint: () -> Unit,
    onTemperature: () -> Unit,
    onReveal: () -> Unit,
    onSimulate: () -> Unit
) {
    BackHandler(onBack = onBack)
    val haptics = LocalHapticFeedback.current

    Scaffold(
        containerColor = Loom.Canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        level.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Loom.Ink
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to levels",
                            tint = Loom.Ink
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onTemperature) {
                        Icon(
                            Icons.Rounded.Thermostat,
                            contentDescription = "Show air temperature",
                            tint = if (ui.showTemperature) Loom.Coral else Loom.Moss
                        )
                    }
                    IconButton(onClick = onHint) {
                        Icon(
                            Icons.Rounded.Lightbulb,
                            contentDescription = "Hint",
                            tint = if (ui.hintShown) Loom.Coral else Loom.Moss
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Loom.Canvas)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ui.progress().forEach { ObjectiveChip(it) }
            }

            AnimatedVisibility(
                visible = ui.hintShown,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LoomCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    background = Color(0xFFFBF3DF)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            level.hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Loom.Ink
                        )
                        if (level.solution.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = onReveal, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text(
                                    "Weave it for me",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Loom.Coral
                                )
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                DioramaBoard(
                    level = level,
                    state = state,
                    threads = ui.threads,
                    phase = phase,
                    modifier = Modifier.fillMaxSize(),
                    armedThread = ui.armed?.takeIf { (ui.remaining[it] ?: 0) > 0 },
                    showTemperature = ui.showTemperature,
                    reducedMotion = reducedMotion,
                    onStrokeComplete = { stroke ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        LoomAudio.play(Sfx.ThreadDraw)
                        onStroke(stroke)
                    }
                )
            }

            ThreadPalette(
                ui = ui,
                onArm = {
                    LoomAudio.play(Sfx.Tap, 0.7f)
                    onArm(it)
                }
            )

            ActionBar(
                canUndo = ui.threads.isNotEmpty(),
                canRedo = ui.redoStack.isNotEmpty(),
                canSimulate = ui.canSimulate,
                onUndo = {
                    LoomAudio.play(Sfx.Tap, 0.7f)
                    onUndo()
                },
                onRedo = {
                    LoomAudio.play(Sfx.Tap, 0.7f)
                    onRedo()
                },
                onClear = {
                    LoomAudio.play(Sfx.Tap, 0.7f)
                    onClear()
                },
                onSimulate = onSimulate
            )
        }
    }
}

@Composable
private fun ThreadPalette(ui: PuzzleUiState, onArm: (ThreadType) -> Unit) {
    val order = listOf(
        ThreadType.WarmFront,
        ThreadType.ColdFront,
        ThreadType.WindBand,
        ThreadType.MoistureRibbon
    ).filter { ui.level?.threads?.containsKey(it) == true }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        order.forEach { type ->
            val left = ui.remaining[type] ?: 0
            val selected = ui.armed == type && left > 0
            val tint = type.ribbonColor().let {
                if (type == ThreadType.WindBand) Loom.WindInk else it
            }
            Surface(
                onClick = { if (left > 0) onArm(type) },
                enabled = left > 0,
                shape = RoundedCornerShape(50),
                color = if (selected) tint.copy(alpha = 0.14f) else Loom.Surface,
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) tint else Loom.Outline
                )
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThreadGlyph(type, if (left > 0) tint else Loom.Outline)
                    Text(
                        type.shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (left > 0) Loom.Ink else Loom.Moss
                    )
                    Text(
                        "×$left",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (left > 0) Loom.Moss else Loom.Outline
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBar(
    canUndo: Boolean,
    canRedo: Boolean,
    canSimulate: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSimulate: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RoundAction(Icons.Rounded.Undo, "Undo last thread", canUndo, onUndo)
        RoundAction(Icons.Rounded.Redo, "Redo thread", canRedo, onRedo)
        RoundAction(Icons.Rounded.CleaningServices, "Clear all threads", canUndo, onClear)
        Spacer(Modifier.width(2.dp))
        LoomButton(
            text = "Simulate",
            onClick = onSimulate,
            enabled = canSimulate,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = Loom.Surface,
        border = BorderStroke(1.dp, Loom.Outline),
        modifier = Modifier.size(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) Loom.Ink else Loom.Outline,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/** Small legend used by the tutorial level so vocabulary follows behaviour. */
@Composable
fun ThreadRuleRow(type: ThreadType, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Loom.Surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ThreadGlyph(type, type.ribbonColor(), 18)
        Column {
            Text(type.label, style = MaterialTheme.typography.labelMedium, color = Loom.Ink)
            Text(type.rule, style = MaterialTheme.typography.bodySmall, color = Loom.Moss)
        }
    }
}
