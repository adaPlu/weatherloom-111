package com.rork.weatherloom.core.sim

import org.junit.Assert.assertNotEquals
import org.junit.Test

class SimStateHashTest {

    private fun assertChanges(mutator: (SimState) -> Unit) {
        val state = SimState(2)
        val before = state.hash()
        mutator(state)
        assertNotEquals(before, state.hash())
    }

    @Test
    fun fingerprintIncludesEveryMutableField() {
        assertChanges { it.temp[0] = 1 }
        assertChanges { it.moisture[0] = 1 }
        assertChanges { it.cloud[0] = 1 }
        assertChanges { it.water[0] = 1 }
        assertChanges { it.storage[0] = 1 }
        assertChanges { it.snow[0] = 1 }
        assertChanges { it.fog[0] = 1 }
        assertChanges { it.windDir[0] = 1 }
        assertChanges { it.windStr[0] = 1 }
        assertChanges { it.bloom[0] = 1 }
        assertChanges { it.bloomTimer[0] = 1 }
        assertChanges { it.freeze[0] = 1 }
        assertChanges { it.frozen[0] = 1 }
        assertChanges { it.precip[0] = 1 }
        assertChanges { it.precipSnow[0] = 1 }
        assertChanges { it.windmillTicks[0] = 1 }
        assertChanges { it.spinning[0] = 1 }
        assertChanges { it.overflowed = true }
        assertChanges { it.beat = 1 }
    }
}
