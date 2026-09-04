package com.rork.weatherloom.data

import kotlinx.serialization.Serializable

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

@Serializable
data class PlayerProgression(
    val xp: Int = 0,
    val awardedLevelXp: Map<String, Int> = emptyMap()
) {
    val milestone: Int get() = PlayerXpRules.milestoneFor(xp)

    /**
     * Grants only the additional cumulative XP represented by a better rating.
     * Replays and lower-rating completions are therefore idempotent and non-farmable.
     */
    fun grantForLevelRating(levelId: String, rating: Rating): XpGrantResult {
        require(levelId.isNotBlank()) { "levelId must not be blank" }

        val targetXp = PlayerXpRules.cumulativeXpFor(rating)
        val alreadyAwarded = awardedLevelXp[levelId]?.coerceAtLeast(0) ?: 0
        val xpGranted = (targetXp - alreadyAwarded).coerceAtLeast(0)
        if (xpGranted == 0) {
            return XpGrantResult(progression = this, xpGranted = 0)
        }

        return XpGrantResult(
            progression = copy(
                xp = xp.coerceAtLeast(0) + xpGranted,
                awardedLevelXp = awardedLevelXp + (levelId to targetXp)
            ),
            xpGranted = xpGranted
        )
    }
}

data class XpGrantResult(
    val progression: PlayerProgression,
    val xpGranted: Int
)
