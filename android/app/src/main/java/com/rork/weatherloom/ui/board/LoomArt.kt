package com.rork.weatherloom.ui.board

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rork.weatherloom.R
import com.rork.weatherloom.core.sim.TerrainType
import com.rork.weatherloom.ui.theme.TerrainPalette

/** Every felted prop that can stand on the board. */
enum class LoomSprite {
    Trees,
    Peak,
    PeakSnow,
    Cottage,
    MillTower,
    MillBlades,
    Bud,
    FlowerOpen,
    FlowerBloom,
    Reeds,
    CloudLight,
    CloudDark
}

/**
 * The photographed wool art the whole game is dressed in. Layouts stay procedural — every
 * hollow has its own terrain — but the *material* is always real needle-felted photography,
 * so the board reads as cloth and craft instead of flat vector fill.
 */
object LoomArt {
    /** Maps a collectible id to its felted portrait. Null for species with no art yet. */
    fun specimenRes(id: String): Int? = when (id) {
        "rainbell" -> R.drawable.spec_rainbell
        "frostfern" -> R.drawable.spec_frostfern
        "cloudmoss" -> R.drawable.spec_cloudmoss
        "sunlace" -> R.drawable.spec_sunlace
        "mistcap" -> R.drawable.spec_mistcap
        "windreed" -> R.drawable.spec_windreed
        "thawlily" -> R.drawable.spec_thawlily
        "galeflax" -> R.drawable.spec_galeflax
        "loomstar" -> R.drawable.spec_loomstar
        else -> null
    }

    /** The wool swatch photographed for each biome. */
    fun tileRes(terrain: TerrainType): Int = when (terrain) {
        TerrainType.Meadow -> R.drawable.tile_meadow
        TerrainType.Crop -> R.drawable.tile_crop
        TerrainType.Village -> R.drawable.tile_village
        TerrainType.Reservoir -> R.drawable.tile_reservoir
        TerrainType.River -> R.drawable.tile_river
        TerrainType.Lake -> R.drawable.tile_lake
        TerrainType.Wetland -> R.drawable.tile_wetland
        TerrainType.Forest -> R.drawable.tile_forest
        TerrainType.Mountain -> R.drawable.tile_mountain
        TerrainType.Stone -> R.drawable.tile_stone
        TerrainType.Road -> R.drawable.tile_road
        TerrainType.BareSoil -> R.drawable.tile_baresoil
    }

    fun spriteRes(sprite: LoomSprite): Int = when (sprite) {
        LoomSprite.Trees -> R.drawable.felt_trees
        LoomSprite.Peak -> R.drawable.felt_peak
        LoomSprite.PeakSnow -> R.drawable.felt_peak_snow
        LoomSprite.Cottage -> R.drawable.felt_cottage
        LoomSprite.MillTower -> R.drawable.felt_mill_tower
        LoomSprite.MillBlades -> R.drawable.felt_mill_blades
        LoomSprite.Bud -> R.drawable.felt_bud
        LoomSprite.FlowerOpen -> R.drawable.felt_flower_open
        LoomSprite.FlowerBloom -> R.drawable.felt_flower_bloom
        LoomSprite.Reeds -> R.drawable.felt_reeds
        LoomSprite.CloudLight -> R.drawable.felt_cloud_light
        LoomSprite.CloudDark -> R.drawable.felt_cloud_dark
    }
}

/**
 * Decoded wool photographs, shared across every screen. The bitmaps are immutable and small
 * (they ship pre-scaled), so one process-wide cache keeps scrolling the level map from
 * re-decoding the same swatch dozens of times.
 */
private object ArtCache {
    private val decoded = HashMap<Int, ImageBitmap>()
    private val options = BitmapFactory.Options().apply { inScaled = false }

    @Synchronized
    fun get(resources: Resources, id: Int): ImageBitmap? = decoded.getOrElse(id) {
        val bmp = runCatching {
            BitmapFactory.decodeResource(resources, id, options).asImageBitmap()
        }.getOrNull()
        if (bmp != null) decoded[id] = bmp
        bmp
    }
}

/**
 * How many cells one wool swatch spans. Deliberately not a whole number, so the weave never
 * lines up with the grid and the felt reads as one hand-cut piece instead of tiling.
 */
private const val WOOL_SPAN_CELLS = 3.37f

/** Every wool photograph the board needs, decoded once. */
class BoardArt(
    private val tiles: Map<TerrainType, ImageBitmap>,
    private val sprites: Map<LoomSprite, ImageBitmap>,
    private val brushes: Map<TerrainType, ShaderBrush>
) {
    fun tile(terrain: TerrainType): ImageBitmap? = tiles[terrain]
    fun sprite(sprite: LoomSprite): ImageBitmap? = sprites[sprite]

    /** The biome's wool as a repeating brush, measured in grid units. */
    fun brush(terrain: TerrainType): ShaderBrush? = brushes[terrain]

    /** Used only if a swatch is somehow missing, so the board never renders blank. */
    fun fallbackColor(terrain: TerrainType): Color = when (terrain) {
        TerrainType.Meadow -> TerrainPalette.Meadow
        TerrainType.Crop -> TerrainPalette.Crop
        TerrainType.Village -> TerrainPalette.Village
        TerrainType.Reservoir -> TerrainPalette.Reservoir
        TerrainType.River -> TerrainPalette.River
        TerrainType.Lake -> TerrainPalette.Lake
        TerrainType.Wetland -> TerrainPalette.Wetland
        TerrainType.Forest -> TerrainPalette.Forest
        TerrainType.Mountain -> TerrainPalette.Mountain
        TerrainType.Stone -> TerrainPalette.Stone
        TerrainType.Road -> TerrainPalette.Road
        TerrainType.BareSoil -> TerrainPalette.BareSoil
    }
}

@Composable
fun rememberBoardArt(): BoardArt {
    val resources = LocalContext.current.resources
    return remember(resources) {
        val tiles = TerrainType.entries.mapNotNull { t ->
            ArtCache.get(resources, LoomArt.tileRes(t))?.let { t to it }
        }.toMap()
        BoardArt(
            tiles = tiles,
            sprites = LoomSprite.entries.mapNotNull { s ->
                ArtCache.get(resources, LoomArt.spriteRes(s))?.let { s to it }
            }.toMap(),
            brushes = tiles.mapValues { (_, bmp) ->
                val shader = ImageShader(bmp, TileMode.Repeated, TileMode.Repeated)
                val scale = WOOL_SPAN_CELLS / bmp.width.toFloat()
                shader.setLocalMatrix(Matrix().apply { setScale(scale, scale) })
                ShaderBrush(shader)
            }
        )
    }
}

/**
 * A repeating wool-fibre brush, used for the mat the diorama sits on.
 * [tile] is how wide one wool swatch should read on screen.
 */
@Composable
fun rememberFeltWeave(tile: Dp = 56.dp): ShaderBrush {
    val weave = ImageBitmap.imageResource(R.drawable.felt_weave)
    val tilePx = with(LocalDensity.current) { tile.toPx() }
    return remember(weave, tilePx) {
        val shader = ImageShader(weave, TileMode.Repeated, TileMode.Repeated)
        val scale = tilePx / weave.width.toFloat()
        shader.setLocalMatrix(Matrix().apply { setScale(scale, scale) })
        ShaderBrush(shader)
    }
}

/**
 * Decodes only the felted portraits the terrarium actually needs, so memory grows with
 * the player's collection instead of loading all nine species up front.
 */
@Composable
fun rememberSpecimenArt(ids: List<String>): Map<String, ImageBitmap> {
    val resources = LocalContext.current.resources
    val key = ids.joinToString(",")
    return remember(key) {
        ids.distinct().mapNotNull { id ->
            val res = LoomArt.specimenRes(id) ?: return@mapNotNull null
            ArtCache.get(resources, res)?.let { id to it }
        }.toMap()
    }
}
