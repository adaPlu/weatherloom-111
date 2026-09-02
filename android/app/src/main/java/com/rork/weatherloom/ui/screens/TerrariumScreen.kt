package com.rork.weatherloom.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.core.level.Collectible
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.ui.board.SpecimenBadge
import com.rork.weatherloom.ui.board.TerrariumScene
import com.rork.weatherloom.ui.components.LoomButton
import com.rork.weatherloom.ui.components.StatPill
import com.rork.weatherloom.ui.theme.Loom

/** Home: the keepsake that grew out of every sky you have woven. */
@Composable
fun TerrariumScreen(
    unlocked: List<Collectible>,
    solvedCount: Int,
    dailyStreak: Int,
    continueLevel: Level?,
    lastCollectible: Collectible?,
    phase: Float,
    reducedMotion: Boolean,
    contentPadding: PaddingValues,
    onContinue: () -> Unit,
    onOpenAlmanac: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            TerrariumScene(
                unlocked = unlocked.map { it.id },
                phase = phase,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                StatPill(
                    icon = Icons.Rounded.LocalFlorist,
                    label = if (solvedCount == 1) "1 puzzle bloomed" else "$solvedCount puzzles bloomed",
                    tint = Loom.Fog
                )
                StatPill(
                    icon = Icons.Rounded.WaterDrop,
                    label = if (dailyStreak == 1) "1-day forecast streak" else "$dailyStreak-day forecast streak",
                    tint = Loom.Moisture
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = greeting(solvedCount),
                style = MaterialTheme.typography.displaySmall,
                color = Loom.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.Cloud,
                    contentDescription = null,
                    tint = Loom.Fog,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = lastCollectible?.let { "${it.name} joined your terrarium" }
                        ?: "Nothing has taken root yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Loom.Moss
                )
            }

            Spacer(Modifier.height(18.dp))
            LoomButton(
                text = continueLevel?.let { "Continue weaving — ${it.name}" } ?: "All skies woven",
                onClick = onContinue,
                enabled = continueLevel != null,
                icon = Icons.Rounded.Cloud,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            if (unlocked.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Loom.Surface,
                    border = BorderStroke(1.dp, Loom.Outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Solve a hollow and the first species will settle under the glass.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Loom.Moss,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(unlocked, key = { it.id }) { c ->
                        SpeciesChip(c, phase, onOpenAlmanac)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SpeciesChip(collectible: Collectible, phase: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Loom.Surface,
        border = BorderStroke(1.dp, Loom.Outline),
        modifier = Modifier.width(96.dp)
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SpecimenBadge(collectible.id, Modifier.size(66.dp), phase)
            Spacer(Modifier.height(6.dp))
            Text(
                collectible.name,
                style = MaterialTheme.typography.labelSmall,
                color = Loom.Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun greeting(solved: Int): String = when {
    solved == 0 -> "Your loom is waiting"
    solved < 4 -> "Your loom is waking up"
    solved < 9 -> "The glass is getting crowded"
    else -> "A whole season under glass"
}
