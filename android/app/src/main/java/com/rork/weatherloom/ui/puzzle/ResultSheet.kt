package com.rork.weatherloom.ui.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.core.level.Collectible
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.core.sim.SimResult
import com.rork.weatherloom.data.Rating
import com.rork.weatherloom.ui.board.SpecimenBadge
import com.rork.weatherloom.ui.components.LoomButton
import com.rork.weatherloom.ui.components.LoomCard
import com.rork.weatherloom.ui.components.RatingMedallions
import com.rork.weatherloom.ui.theme.Loom

/**
 * How the run ended, in one breath: the verdict, the rating you earned, why the
 * weather did what it did, and whatever took root in your terrarium.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSheet(
    level: Level,
    result: SimResult,
    rating: Rating,
    explanation: List<String>,
    reward: Collectible?,
    sheetState: SheetState,
    phase: Float,
    hasNextLevel: Boolean,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onReplay: () -> Unit,
    onAdjust: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Loom.Surface,
        contentColor = Loom.Ink
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            if (result.solved)
                                listOf(Color(0xFFCFE0C4), Color(0xFFE7E2CD), Color(0xFFCBDCE3))
                            else
                                listOf(Color(0xFFE6DDD0), Color(0xFFEFE6D8))
                        )
                    )
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = verdict(level, result),
                style = MaterialTheme.typography.headlineSmall,
                color = Loom.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subVerdict(level, result),
                style = MaterialTheme.typography.bodyMedium,
                color = Loom.Moss,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (result.solved) {
                Spacer(Modifier.height(20.dp))
                RatingMedallions(rating, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))
            LoomCard(Modifier.fillMaxWidth(), background = Loom.SurfaceSunk) {
                Column(Modifier.padding(16.dp)) {
                    explanation.forEachIndexed { i, line ->
                        if (i > 0) {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = Loom.Outline)
                            Spacer(Modifier.height(10.dp))
                        }
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = Loom.Ink)
                    }
                }
            }

            if (reward != null) {
                Spacer(Modifier.height(14.dp))
                LoomCard(Modifier.fillMaxWidth(), background = Color(0xFFF3F0E2)) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SpecimenBadge(reward.id, Modifier.size(76.dp), phase)
                        Column {
                            Text(
                                "${reward.name} takes root in your terrarium",
                                style = MaterialTheme.typography.titleMedium,
                                color = Loom.Ink
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                reward.flavour,
                                style = MaterialTheme.typography.bodySmall,
                                color = Loom.Moss
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (result.solved) {
                    LoomButton(
                        text = if (hasNextLevel) "Next level" else "Back to biomes",
                        onClick = onNext,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryAction("Replay", onReplay, Modifier.weight(1f))
                } else {
                    LoomButton(
                        text = "Adjust the threads",
                        onClick = onAdjust,
                        modifier = Modifier.weight(1.4f)
                    )
                    SecondaryAction("Replay", onReplay, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Loom.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Loom.Outline)
    ) {
        Row(
            Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Replay,
                contentDescription = null,
                tint = Loom.Ink,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = Loom.Ink)
        }
    }
}

private fun verdict(level: Level, result: SimResult): String {
    if (!result.solved) {
        val missed = result.progress.firstOrNull { !it.met } ?: return "Not quite yet"
        return "The ${missed.spec.label.lowercase()} did not settle"
    }
    val headline = result.progress.firstOrNull()
    return when {
        headline == null -> "The hollow is content"
        else -> "${headline.spec.label} settled at ${headline.current}"
    }
}

private fun subVerdict(level: Level, result: SimResult): String {
    if (!result.solved) return "Scrub the timeline to see the moment it went wrong."
    val extras = result.progress.drop(1)
    if (extras.isEmpty()) return "Everything you asked of the sky, the sky did."
    return extras.joinToString(" · ") { "${it.spec.label} ${it.current}" }
}
