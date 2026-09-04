package com.rork.weatherloom.core.terrarium.reaction

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versioned, static, data-driven Terrarium reaction content. */
@Serializable
data class ReactionCatalog(
    val schemaVersion: Int,
    val rules: List<ReactionRule>
) {
    init {
        require(schemaVersion > 0) { "reaction catalog schemaVersion must be positive" }
        val ruleIds = rules.map { it.id }
        require(ruleIds.distinct().size == ruleIds.size) {
            "reaction rule ids must be unique"
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): ReactionCatalog =
            json.decodeFromString(serializer(), text)
    }
}
