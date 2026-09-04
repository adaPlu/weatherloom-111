package com.rork.weatherloom.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerXpProgressionTest {

    @Test
    fun ratingRewardsAreCumulativeAndMilestonesHaveStableBoundaries() {
        assertEquals(0, PlayerXpRules.cumulativeXpFor(Rating.None))
        assertEquals(100, PlayerXpRules.cumulativeXpFor(Rating.Seedling))
        assertEquals(150, PlayerXpRules.cumulativeXpFor(Rating.Bloom))
        assertEquals(200, PlayerXpRules.cumulativeXpFor(Rating.Flourish))

        assertEquals(0, PlayerXpRules.milestoneFor(0))
        assertEquals(0, PlayerXpRules.milestoneFor(499))
        assertEquals(1, PlayerXpRules.milestoneFor(500))
        assertEquals(1, PlayerXpRules.milestoneFor(999))
        assertEquals(2, PlayerXpRules.milestoneFor(1000))
    }

    @Test
    fun replayCannotFarmXpAndRatingUpgradesGrantOnlyTheDifference() {
        val start = PlayerProgression()

        val firstSolve = start.grantForLevelRating("c1-1", Rating.Seedling)
        assertEquals(100, firstSolve.progression.xp)
        assertEquals(100, firstSolve.xpGranted)

        val repeatedSolve = firstSolve.progression.grantForLevelRating("c1-1", Rating.Seedling)
        assertEquals(100, repeatedSolve.progression.xp)
        assertEquals(0, repeatedSolve.xpGranted)

        val bloomUpgrade = repeatedSolve.progression.grantForLevelRating("c1-1", Rating.Bloom)
        assertEquals(150, bloomUpgrade.progression.xp)
        assertEquals(50, bloomUpgrade.xpGranted)

        val lowerReplay = bloomUpgrade.progression.grantForLevelRating("c1-1", Rating.Seedling)
        assertEquals(150, lowerReplay.progression.xp)
        assertEquals(0, lowerReplay.xpGranted)

        val flourishUpgrade = lowerReplay.progression.grantForLevelRating("c1-1", Rating.Flourish)
        assertEquals(200, flourishUpgrade.progression.xp)
        assertEquals(50, flourishUpgrade.xpGranted)
        assertEquals(200, flourishUpgrade.progression.awardedLevelXp.getValue("c1-1"))
    }
}
