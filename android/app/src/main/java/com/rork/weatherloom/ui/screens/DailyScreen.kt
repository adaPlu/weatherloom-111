package com.rork.weatherloom.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.core.level.DailyForecast
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.ui.components.LoomButton
import com.rork.weatherloom.ui.components.LoomCard
import com.rork.weatherloom.ui.components.SectionHeading
import com.rork.weatherloom.ui.theme.Loom

/** One board for everybody, generated from the date. Works with no network at all. */
@Composable
fun DailyScreen(
    today: Level?,
    todayKey: String,
    completedToday: Boolean,
    streak: Int,
    history: Set<String>,
    contentPadding: PaddingValues,
    onPlay: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Loom.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
    ) {
        SectionHeading(
            "Daily Forecast",
            "The same sky for every weaver, every day."
        )
        Spacer(Modifier.height(16.dp))

        LoomCard(Modifier.fillMaxWidth()) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFCBDCE3), Color(0xFFE6E1CE), Color(0xFFDCCFC0))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        DailyForecast.prettyDate(todayKey),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Loom.Ink
                    )
                }
                Column(Modifier.padding(18.dp)) {
                    Text(
                        today?.brief ?: "The loom is resting today.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Loom.Ink
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        today?.let { lvl ->
                            lvl.objectives.joinToString(" · ") { "${it.label} ${it.target}" }
                        } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Loom.Moss
                    )
                    Spacer(Modifier.height(16.dp))
                    if (completedToday) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFDDE9D5))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color(0xFF4E6E45),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Today's forecast is settled.",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF3F5F3A)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    LoomButton(
                        text = if (completedToday) "Weave it again" else "Weave today's sky",
                        onClick = onPlay,
                        enabled = today != null,
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.fillMaxWidth(),
                        container = if (completedToday) Loom.Moisture else Loom.Coral
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = Loom.Coral,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (streak == 1) "1 day in a row" else "$streak days in a row",
                style = MaterialTheme.typography.titleMedium,
                color = Loom.Ink
            )
        }

        Spacer(Modifier.height(12.dp))
        LoomCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "The last two weeks",
                    style = MaterialTheme.typography.labelMedium,
                    color = Loom.Moss
                )
                Spacer(Modifier.height(12.dp))
                val keys = (13 downTo 0).map { DailyForecast.dayKey(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEach { key ->
                        val done = key in history
                        val isToday = key == todayKey
                        Box(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    when {
                                        done -> Loom.Moisture
                                        isToday -> Loom.CoralSoft
                                        else -> Loom.SurfaceSunk
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                key.takeLast(2).trimStart('0'),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (done) Loom.Surface else Loom.Moss,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Loom.Surface,
            border = BorderStroke(1.dp, Loom.Outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Daily boards are grown from validated hollows and the calendar date, never from " +
                    "random tiles — so there is always a solution waiting.",
                style = MaterialTheme.typography.bodySmall,
                color = Loom.Moss,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
