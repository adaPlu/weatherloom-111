package com.rork.weatherloom.core.sim

/**
 * Mutable environmental state of every cell for one logical beat.
 * All fields are integer-driven so results are bit-identical on any device.
 */
class SimState(val size: Int) {
    val temp = IntArray(size)
    val moisture = IntArray(size)
    val cloud = IntArray(size)
    val water = IntArray(size)
    val storage = IntArray(size)
    val snow = IntArray(size)
    val fog = IntArray(size)
    val windDir = IntArray(size)
    val windStr = IntArray(size)
    val bloom = IntArray(size)
    val bloomTimer = IntArray(size)
    val freeze = IntArray(size)
    val frozen = IntArray(size)
    val precip = IntArray(size)
    val precipSnow = IntArray(size)
    val windmillTicks = IntArray(size)
    val spinning = IntArray(size)
    var overflowed: Boolean = false
    var beat: Int = 0

    fun copy(): SimState {
        val other = SimState(size)
        temp.copyInto(other.temp)
        moisture.copyInto(other.moisture)
        cloud.copyInto(other.cloud)
        water.copyInto(other.water)
        storage.copyInto(other.storage)
        snow.copyInto(other.snow)
        fog.copyInto(other.fog)
        windDir.copyInto(other.windDir)
        windStr.copyInto(other.windStr)
        bloom.copyInto(other.bloom)
        bloomTimer.copyInto(other.bloomTimer)
        freeze.copyInto(other.freeze)
        frozen.copyInto(other.frozen)
        precip.copyInto(other.precip)
        precipSnow.copyInto(other.precipSnow)
        windmillTicks.copyInto(other.windmillTicks)
        spinning.copyInto(other.spinning)
        other.overflowed = overflowed
        other.beat = beat
        return other
    }

    /** Order-independent fingerprint used to prove replays are deterministic. */
    fun hash(): Long {
        var h = 1125899906842597L
        fun mix(a: IntArray) {
            for (v in a) h = h * 31 + v
        }
        mix(temp); mix(moisture); mix(cloud); mix(water); mix(storage)
        mix(snow); mix(fog); mix(windDir); mix(windStr); mix(bloom)
        mix(freeze); mix(frozen); mix(precip); mix(precipSnow); mix(windmillTicks)
        h = h * 31 + if (overflowed) 1 else 0
        return h
    }
}

/** A notable, explainable thing that happened at a specific beat. */
data class CausalEvent(
    val beat: Int,
    val kind: EventKind,
    val cell: Int,
    val text: String
)

enum class EventKind {
    CloudsCollide,
    RainBegins,
    SnowBegins,
    RunoffReaches,
    FlowerBloomed,
    WindmillTurning,
    FogCleared,
    CropFrozen,
    Overflow,
    Flooded
}
