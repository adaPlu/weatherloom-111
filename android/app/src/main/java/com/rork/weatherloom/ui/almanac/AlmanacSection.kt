package com.rork.weatherloom.ui.almanac

enum class AlmanacSection(
    val label: String,
    val subtitle: String
) {
    Species(
        label = "Species",
        subtitle = "The living specimens you have encountered in Weatherloom."
    ),
    Weather(
        label = "Weather",
        subtitle = "Authored field notes for the weather carried by a Terrarium echo."
    ),
    Discoveries(
        label = "Discoveries",
        subtitle = "Small Terrarium secrets revealed by conditions you create."
    )
}
