package com.rork.weatherloom.data

/**
 * Deterministic authored-level XP values.
 *
 * Values are cumulative for a level's best rating, so rating upgrades grant only the
 * difference and replaying the same or a lower rating cannot create farmable XP.
 */
object PlayerXpRules {
    const val SEEDLING_XP = 100
    const val BLOOM_XP = 150
    const val FLOURISH_XP = 200
    const val MILESTONE_STEP_XP = 500

    fun cumulativeXpFor(rating: Rating): Int = when (rating) {
        Rating.None -> 0
        Rating.Seedling -> SEEDLING_XP
        Rating.Bloom -> BLOOM_XP
        Rating.Flourish -> FLOURISH_XP
    }

    fun milestoneFor(xp: Int): Int = xp.coerceAtLeast(0) / MILESTONE_STEP_XP
}
