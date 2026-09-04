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
}
