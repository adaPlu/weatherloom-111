package com.rork.weatherloom.core.terrarium.reaction

import com.rork.weatherloom.core.weather.WeatherEchoKind
import kotlinx.serialization.Serializable

@Serializable
data class ReactionRequirements(
    val itemTags: List<String> = emptyList(),
    val weatherKinds: List<WeatherEchoKind> = emptyList(),
    val environmentModifiers: List<String> = emptyList()
) {
    init {
        requireCanonicalStableIds(itemTags, "reaction item tags")
        requireCanonicalStableIds(environmentModifiers, "reaction environment modifiers")
        require(weatherKinds.distinct().size == weatherKinds.size) {
            "reaction weather kinds must be unique"
        }
        val canonicalWeather = WeatherEchoKind.entries.filter { it in weatherKinds }
        require(weatherKinds == canonicalWeather) {
            "reaction weather kinds must use canonical ordering"
        }
    }
}

@Serializable
enum class DurableReactionEventKind {
    DiscoveryCandidate
}

@Serializable
data class DurableReactionEventDefinition(
    val id: String,
    val kind: DurableReactionEventKind,
    val payloadId: String
) {
    init {
        requireStableReactionId(id, "durable reaction event id")
        requireStableReactionId(payloadId, "durable reaction payload id")
    }
}

@Serializable
data class ReactionRuleOutput(
    val visualTags: List<String> = emptyList(),
    val durableEvents: List<DurableReactionEventDefinition> = emptyList()
) {
    init {
        requireCanonicalStableIds(visualTags, "reaction visual tags")
        val durableIds = durableEvents.map { it.id }
        require(durableIds.distinct().size == durableIds.size) {
            "reaction durable event ids must be unique within a rule"
        }
        require(durableIds == durableIds.sorted()) {
            "reaction durable events must use canonical id ordering"
        }
        require(visualTags.isNotEmpty() || durableEvents.isNotEmpty()) {
            "reaction output must contain a visual tag or durable event"
        }
    }
}

@Serializable
data class ReactionRule(
    val id: String,
    val requires: ReactionRequirements,
    val result: ReactionRuleOutput
) {
    init {
        requireStableReactionId(id, "reaction rule id")
        require(
            requires.itemTags.isNotEmpty() ||
                requires.weatherKinds.isNotEmpty() ||
                requires.environmentModifiers.isNotEmpty()
        ) { "reaction rule must contain at least one requirement" }
    }
}

@Serializable
data class ReactionVisualState(
    val instanceId: String,
    val visualTags: List<String>
) {
    init {
        require(instanceId.isNotBlank()) { "reaction visual instanceId must not be blank" }
        requireCanonicalStableIds(visualTags, "reaction visual state tags")
        require(visualTags.isNotEmpty()) { "reaction visual state must contain at least one tag" }
    }
}

@Serializable
data class DurableReactionEvent(
    val id: String,
    val kind: DurableReactionEventKind,
    val payloadId: String,
    val ruleId: String,
    val sourceInstanceId: String
) {
    init {
        requireStableReactionId(id, "durable reaction event id")
        requireStableReactionId(payloadId, "durable reaction payload id")
        requireStableReactionId(ruleId, "reaction rule id")
        require(sourceInstanceId.isNotBlank()) { "durable reaction sourceInstanceId must not be blank" }
    }
}

@Serializable
data class ReactionResult(
    val visualStates: List<ReactionVisualState> = emptyList(),
    val pendingDurableEvents: List<DurableReactionEvent> = emptyList()
) {
    init {
        val visualIds = visualStates.map { it.instanceId }
        require(visualIds.distinct().size == visualIds.size) {
            "reaction visual states must contain unique instance ids"
        }
        require(visualIds == visualIds.sorted()) {
            "reaction visual states must use canonical instance ordering"
        }

        val eventIds = pendingDurableEvents.map { it.id }
        require(eventIds.distinct().size == eventIds.size) {
            "pending durable reaction event ids must be unique"
        }
        require(eventIds == eventIds.sorted()) {
            "pending durable reaction events must use canonical id ordering"
        }
    }
}

private fun requireCanonicalStableIds(values: List<String>, label: String) {
    require(values.distinct().size == values.size) { "$label must be unique" }
    require(values.all(::isStableReactionId)) { "$label must use stable lowercase ids" }
    require(values == values.sorted()) { "$label must use canonical ordering" }
}
