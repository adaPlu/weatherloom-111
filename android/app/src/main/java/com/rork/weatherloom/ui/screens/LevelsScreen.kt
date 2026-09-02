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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.core.level.Chapter
import com.rork.weatherloom.core.level.Level
import com.rork.weatherloom.core.sim.Feature
import com.rork.weatherloom.core.sim.TerrainType
import com.rork.weatherloom.data.Rating
import com.rork.weatherloom.ui.board.BoardGeometry
import com.rork.weatherloom.ui.board.LoomSprite
import com.rork.weatherloom.ui.board.drawElevationLight
import com.rork.weatherloom.ui.board.drawFeltProp
import com.rork.weatherloom.ui.board.drawWoolGround
import com.rork.weatherloom.ui.board.rememberBoardArt
import com.rork.weatherloom.ui.board.rememberBoardRegions
import com.rork.weatherloom.ui.components.SectionHeading
import com.rork.weatherloom.ui.theme.Loom
import com.rork.weatherloom.ui.theme.TerrainPalette

data class LevelEntry(val level: Level, val rating: Rating, val unlocked: Boolean)

/** Biome map: every chapter, every hollow, and how well you settled it. */
@Composable
fun LevelsScreen(
    chapters: List<Chapter>,
    entries: List<LevelEntry>,
    contentPadding: PaddingValues,
    onOpen: (Level) -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Loom.Canvas),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeading(
                "Biomes",
                "Six chapters of tiny weather. Each one teaches a new rule."
            )
            Spacer(Modifier.height(6.dp))
        }

        chapters.forEach { chapter ->
            val chapterEntries = entries.filter { it.level.chapter == chapter.index }
            if (chapterEntries.isEmpty()) return@forEach
            item(key = "ch-${chapter.index}") {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(50))
                            .background(chapterTint(chapter.index)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${chapter.index}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Loom.Surface
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Loom.Ink
                        )
                        Text(
                            chapter.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Loom.Moss
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            items(chapterEntries, key = { it.level.id }) { entry ->
                LevelCard(entry, onOpen)
            }
        }
    }
}

@Composable
private fun LevelCard(entry: LevelEntry, onOpen: (Level) -> Unit) {
    val level = entry.level
    Surface(
        onClick = { if (entry.unlocked) onOpen(level) },
        enabled = entry.unlocked,
        shape = RoundedCornerShape(22.dp),
        color = if (entry.unlocked) Loom.Surface else Color(0xFFEFEADE),
        border = BorderStroke(1.dp, Loom.Outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                LevelThumb(level, entry.unlocked)
                if (!entry.unlocked) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFFEFEADE).copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = Loom.Moss,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    level.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (entry.unlocked) Loom.Ink else Loom.Moss
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (entry.unlocked) level.brief else "Settle the hollow before this one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Loom.Moss,
                    maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    level.objectives.take(3).forEach { spec ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Loom.SurfaceSunk
                        ) {
                            Text(
                                spec.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Loom.Moss,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            RatingDots(entry.rating)
        }
    }
}

@Composable
private fun RatingDots(rating: Rating) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) { i ->
            val filled = rating.ordinal > i
            Box(
                Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (filled) Color(0xFFE0B75C) else Loom.Outline)
            )
        }
    }
}

/** A pinch of the actual board — the same wool, so a thumbnail is a real swatch of the hollow. */
@Composable
private fun LevelThumb(level: Level, unlocked: Boolean) {
    val art = rememberBoardArt()
    val regions = rememberBoardRegions(level)
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val cell = size.minDimension / maxOf(level.width, level.height).toFloat()
        val geo = BoardGeometry(
            origin = Offset(
                (size.width - cell * level.width) / 2f,
                (size.height - cell * level.height) / 2f
            ),
            cell = cell,
            cols = level.width,
            rows = level.height
        )
        drawRect(Color(0xFFE9E1CE))
        drawWoolGround(geo, art, regions, stitch = false)
        drawElevationLight(geo, regions)
        // Landmarks last, so a hollow is recognisable even at 64dp.
        for (i in level.cells.indices) {
            val c = level.cells[i]
            val r = geo.rect(i)
            val sprite = when {
                c.feature == Feature.Flower -> LoomSprite.FlowerBloom
                c.feature == Feature.Windmill -> LoomSprite.MillTower
                c.feature == Feature.House -> LoomSprite.Cottage
                c.terrain == TerrainType.Mountain -> LoomSprite.Peak
                c.terrain == TerrainType.Forest -> LoomSprite.Trees
                else -> null
            } ?: continue
            art.sprite(sprite)?.let {
                val span = if (sprite == LoomSprite.Peak) cell * 1.4f else cell * 1.1f
                drawFeltProp(it, r.center.x, r.bottom + cell * 0.1f, span)
            }
        }
        if (!unlocked) {
            drawRect(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x22000000))))
        }
    }
}

private fun chapterTint(index: Int): Color = when (index) {
    1 -> Loom.Coral
    2 -> Loom.WindInk
    3 -> Loom.Cold
    4 -> Color(0xFFB58A3E)
    5 -> Loom.Moisture
    else -> Color(0xFF8A76B4)
}
