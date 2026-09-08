package com.rork.weatherloom.ui.theme

import androidx.compose.ui.graphics.Color

/** Weatherloom palette: handcrafted felt-and-clay diorama, warm parchment surfaces. */
object Loom {
    val Canvas = Color(0xFFF5EFE3)
    val Surface = Color(0xFFFDFBF4)
    val SurfaceSunk = Color(0xFFEFE8DA)
    val Ink = Color(0xFF2B3A36)

    // Decorative felt hues. Keep these for art, fills, ribbons, and non-text accents.
    val Moss = Color(0xFF7A8B7F)
    val Coral = Color(0xFFE2694F)
    val CoralSoft = Color(0xFFF6DCD3)
    val Cold = Color(0xFF4E8FAE)
    val ColdSoft = Color(0xFFD8E7EE)
    val WindCream = Color(0xFFEDE3CC)
    val WindInk = Color(0xFF8A7F63)
    val Moisture = Color(0xFF5FA8A0)
    val MoistureSoft = Color(0xFFD6EAE7)

    // Accessible semantic hues for small text and foreground actions on warm surfaces.
    // Each text value is >= 4.5:1 against Canvas, Surface, and SurfaceSunk.
    val TextMuted = Color(0xFF52635A)
    val TextAccent = Color(0xFFAC3E2C)
    val TextCold = Color(0xFF356C85)
    val TextMoisture = Color(0xFF316B64)

    // Strong containers support small light text at >= 4.5:1 while preserving hue families.
    val CoralStrong = TextAccent
    val ColdStrong = TextCold
    val MoistureStrong = TextMoisture
    val WindStrong = Color(0xFF665C43)
    val OchreStrong = Color(0xFF765612)
    val PurpleStrong = Color(0xFF65528D)

    val Meadow = Color(0xFF9DBB8A)
    val Fog = Color(0xFFC9C3D6)
    val Outline = Color(0xFFE0D7C4)
    val NightSky = Color(0xFF1F2A2F)
}

/** Felt colours for each terrain type, before elevation shading. */
object TerrainPalette {
    val Meadow = Color(0xFFA8C08C)
    val Crop = Color(0xFFC2B58D)
    val Village = Color(0xFFC9B69C)
    val Reservoir = Color(0xFF6FA3B8)
    val River = Color(0xFF7FB3C4)
    val Lake = Color(0xFF5F94AC)
    val Wetland = Color(0xFF8CAE93)
    val Forest = Color(0xFF6E8F65)
    val Mountain = Color(0xFF9BA394)
    val Stone = Color(0xFFB2B0A6)
    val Road = Color(0xFFC4B59D)
    val BareSoil = Color(0xFFBCA98C)
}
