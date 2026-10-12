package com.rork.weatherloom.ui.puzzle

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rork.weatherloom.core.level.LevelLibrary
import com.rork.weatherloom.core.sim.SimulationEngine
import com.rork.weatherloom.ui.board.toThread
import com.rork.weatherloom.ui.components.rememberLoomPhase

/**
 * Hosts one puzzle attempt across its three chrome-less phases: drawing, full-screen
 * playback, and the result sheet. None of them show the tab bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleRoute(
    levelId: String,
    reducedMotion: Boolean,
    onExit: () -> Unit,
    onOpenLevel: (String) -> Unit
) {
    val vm: PuzzleViewModel = viewModel()
    LaunchedEffect(levelId) { vm.load(levelId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val phase = rememberLoomPhase(reducedMotion)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val level = ui.level ?: return
    val state = ui.frame() ?: SimulationEngine.initialState(level)

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = ui.phase != Phase.Draw,
            transitionSpec = {
                if (targetState) {
                    (fadeIn() + slideInVertically { it / 6 }) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith (fadeOut() + slideOutVertically { it / 6 })
                }
            },
            label = "puzzle-phase"
        ) { running ->
            if (!running) {
                PuzzleDrawScreen(
                    level = level,
                    state = state,
                    ui = ui,
                    phase = phase,
                    reducedMotion = reducedMotion,
                    onBack = onExit,
                    onArm = vm::arm,
                    onStroke = { stroke ->
                        ui.armed?.let { vm.addThread(stroke.toThread(it)) }
                    },
                    onUndo = vm::undo,
                    onRedo = vm::redo,
                    onClear = vm::clear,
                    onHint = vm::toggleHint,
                    onTemperature = vm::toggleTemperature,
                    onReveal = vm::revealSolution,
                    onSimulate = vm::simulate
                )
            } else {
                val result = ui.result
                if (result != null) {
                    PlaybackScreen(
                        level = level,
                        result = result,
                        state = state,
                        ui = ui,
                        phase = phase,
                        reducedMotion = reducedMotion,
                        onClose = vm::backToDraw,
                        onTogglePlay = vm::togglePlay,
                        onSpeed = vm::cycleSpeed,
                        onRestart = vm::restart,
                        onScrub = vm::scrubTo,
                        onEvent = { e -> vm.jumpToEvent(e.beat, e.cell) }
                    )
                }
            }
        }

        val result = ui.result
        if (ui.phase == Phase.Result && result != null) {
            val next = if (ui.isDaily) null else LevelLibrary.next(level.id)
            ResultSheet(
                level = level,
                result = result,
                rating = ui.rating,
                explanation = ui.explanation,
                reward = ui.newCollectible?.let { LevelLibrary.collectible(it) },
                sheetState = sheetState,
                phase = phase,
                hasNextLevel = next != null,
                onDismiss = vm::dismissResult,
                onNext = {
                    if (next != null) onOpenLevel(next.id) else onExit()
                },
                onReplay = vm::replay,
                onAdjust = vm::backToDraw
            )
        }
    }
}
