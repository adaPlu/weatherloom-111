package com.rork.weatherloom.core.terrarium

import kotlinx.serialization.Serializable

@Serializable
enum class GrowthStage {
    Seed,
    Sprout,
    Young,
    Mature,
    Bloom
}

@Serializable
data class GrowthProfile(
    val id: String,
    val stages: List<GrowthStage>,
    val pulsesPerStage: Int = 1
) {
    init {
        require(id.isNotBlank()) { "growth profile id must not be blank" }
        require(stages.isNotEmpty()) { "growth profile must contain at least one stage" }
        require(stages.size == stages.distinct().size) { "growth profile stages must be unique" }
        require(pulsesPerStage > 0) { "pulsesPerStage must be positive" }
    }
}

/**
 * Durable biological progression. It is deliberately independent from x/y,
 * footprint, depth, rotation, and every other placement concern.
 */
@Serializable
data class GrowthState(
    val instanceId: String,
    val growthProfileId: String,
    val stageIndex: Int = 0,
    val growthPulsesApplied: Int = 0,
    val lastRelevantEchoId: String? = null,
    val appliedEchoIds: List<String> = emptyList()
) {
    init {
        require(instanceId.isNotBlank()) { "growth instanceId must not be blank" }
        require(growthProfileId.isNotBlank()) { "growthProfileId must not be blank" }
        require(stageIndex >= 0) { "growth stageIndex must not be negative" }
        require(growthPulsesApplied >= 0) { "growthPulsesApplied must not be negative" }
        lastRelevantEchoId?.let { require(it.isNotBlank()) { "lastRelevantEchoId must not be blank" } }
        require(appliedEchoIds.all { it.isNotBlank() }) { "appliedEchoIds must not contain blanks" }
        require(appliedEchoIds.size == appliedEchoIds.distinct().size) {
            "appliedEchoIds must be unique"
        }
    }
}
