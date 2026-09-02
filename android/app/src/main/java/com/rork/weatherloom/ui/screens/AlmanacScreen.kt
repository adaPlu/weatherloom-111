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
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.core.level.Collectible
import com.rork.weatherloom.core.sim.ThreadType
import com.rork.weatherloom.ui.board.SpecimenBadge
import com.rork.weatherloom.ui.board.ribbonColor
import com.rork.weatherloom.ui.components.SectionHeading
import com.rork.weatherloom.ui.components.ThreadGlyph
import com.rork.weatherloom.ui.theme.Loom

/** The collection, the rulebook, and the accessibility switches, in one quiet ledger. */
@Composable
fun AlmanacScreen(
    collectibles: List<Collectible>,
    discovered: Set<String>,
    reducedMotion: Boolean,
    musicEnabled: Boolean,
    soundEnabled: Boolean,
    contentPadding: PaddingValues,
    phase: Float,
    onReducedMotion: (Boolean) -> Unit,
    onMusicEnabled: (Boolean) -> Unit,
    onSoundEnabled: (Boolean) -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Loom.Canvas),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeading(
                "Almanac",
                "${discovered.size} of ${collectibles.size} species recorded."
            )
            Spacer(Modifier.height(6.dp))
        }

        items(collectibles, key = { it.id }) { c ->
            val found = c.id in discovered
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (found) Loom.Surface else Color(0xFFEFEADE),
                border = BorderStroke(1.dp, Loom.Outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (found) {
                            SpecimenBadge(c.id, Modifier.size(72.dp), phase)
                        } else {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Loom.SurfaceSunk),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.HelpOutline,
                                    contentDescription = null,
                                    tint = Loom.Outline,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (found) c.name else "Undiscovered",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (found) Loom.Ink else Loom.Moss
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (found) c.flavour else c.unlock,
                            style = MaterialTheme.typography.bodySmall,
                            color = Loom.Moss
                        )
                        if (found) {
                            Spacer(Modifier.height(6.dp))
                            Surface(shape = RoundedCornerShape(50), color = Loom.SurfaceSunk) {
                                Text(
                                    c.biome,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Loom.Moss,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeading("The rules, in full", "Nothing in Weatherloom is hidden from you.")
            Spacer(Modifier.height(10.dp))
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Loom.Surface,
                border = BorderStroke(1.dp, Loom.Outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThreadType.entries.forEach { type ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(type.ribbonColor().copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                ThreadGlyph(
                                    type,
                                    if (type == ThreadType.WindBand) Loom.WindInk else type.ribbonColor(),
                                    16
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Column {
                                Text(
                                    type.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Loom.Ink
                                )
                                Text(
                                    type.rule,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Loom.Moss
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Loom.Surface,
                border = BorderStroke(1.dp, Loom.Outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    WORLD_RULES.forEach { rule ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Loom.Moisture)
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                rule,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Loom.Ink
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeading("Comfort")
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Loom.Surface,
                border = BorderStroke(1.dp, Loom.Outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ComfortSwitch(
                        title = "Reduced motion",
                        detail = "Stills the drifting clouds and swaying reeds. The simulation is unchanged.",
                        checked = reducedMotion,
                        onCheckedChange = onReducedMotion
                    )
                    ComfortSwitch(
                        title = "Music",
                        detail = "The quiet piano bed that plays under everything.",
                        checked = musicEnabled,
                        onCheckedChange = onMusicEnabled
                    )
                    ComfortSwitch(
                        title = "Weather and sounds",
                        detail = "Rain, wind, stitches and chimes. Turning this off leaves the hollow silent.",
                        checked = soundEnabled,
                        onCheckedChange = onSoundEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun ComfortSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Loom.Ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Loom.Moss)
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Loom.Surface,
                checkedTrackColor = Loom.Moisture,
                uncheckedThumbColor = Loom.Surface,
                uncheckedTrackColor = Loom.Outline
            )
        )
    }
}

private val WORLD_RULES = listOf(
    "Warm moist air meeting cold air makes clouds.",
    "A cloud heavy enough lets go of its water.",
    "If it is freezing where the water falls, it falls as snow.",
    "Wind pushes clouds and fog one tile each beat.",
    "Strong wind clears fog, and so does warm air, more slowly.",
    "Mountains squeeze rain from moist clouds on the side the wind comes from.",
    "Rainwater runs downhill, and spreads sideways once it is deep.",
    "Brooks, wetlands and reservoirs pull in the water beside them.",
    "Flowers need water and mild air for two beats running.",
    "Crops freeze after four beats below zero.",
    "Snow melts in warm air, and the meltwater runs downhill too.",
    "Higher ground is colder. Two steps up sheds a degree."
)
