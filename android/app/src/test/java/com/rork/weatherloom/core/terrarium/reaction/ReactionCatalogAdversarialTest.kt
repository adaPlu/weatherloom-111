package com.rork.weatherloom.core.terrarium.reaction

import com.rork.weatherloom.core.terrarium.GrowthState
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumLayout
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionCatalogAdversarialTest {

    @Test
    fun conflictingDefinitionsForSameDurableEventIdAreRejected() {
        val first = rule(
            id = "rainbell-first",
            event = DurableReactionEventDefinition(
                id = "discovery.shared",
                kind = DurableReactionEventKind.DiscoveryCandidate,
                payloadId = "first_discovery"
            )
        )
        val conflicting = rule(
            id = "rainbell-second",
            event = DurableReactionEventDefinition(
                id = "discovery.shared",
                kind = DurableReactionEventKind.DiscoveryCandidate,
                payloadId = "different_discovery"
            )
        )

        assertIllegalArgument {
            ReactionCatalog(schemaVersion = 1, rules = listOf(first, conflicting))
        }
    }

    @Test
    fun identicalDurableDefinitionMayBeSharedAcrossIndependentRules() {
        val shared = DurableReactionEventDefinition(
            id = "discovery.shared",
            kind = DurableReactionEventKind.DiscoveryCandidate,
            payloadId = "shared_discovery"
        )
        val catalog = ReactionCatalog(
            schemaVersion = 1,
            rules = listOf(
                rule("rainbell-first", shared),
                rule("rainbell-second", shared)
            )
        )

        assertEquals(2, catalog.rules.size)
    }

    @Test
    fun duplicateRuleIdsAreRejected() {
        val event = DurableReactionEventDefinition(
            id = "discovery.one",
            kind = DurableReactionEventKind.DiscoveryCandidate,
            payloadId = "one"
        )

        assertIllegalArgument {
            ReactionCatalog(
                schemaVersion = 1,
                rules = listOf(rule("same-rule", event), rule("same-rule", event))
            )
        }
    }

    @Test
    fun duplicateGrowthInstanceInputsAreRejectedBeforeEvaluation() {
        val growth = GrowthState(
            instanceId = "plant-001",
            growthProfileId = "botanical-five-stage"
        )

        assertIllegalArgument {
            ReactionEngine.evaluate(
                layout = TerrariumLayout(),
                growthStates = listOf(growth, growth),
                environment = EnvironmentState(weatherEcho = rainEcho()),
                catalog = TerrariumCatalog(
                    schemaVersion = 1,
                    growthProfiles = emptyList(),
                    items = emptyList()
                ),
                reactions = ReactionCatalog(schemaVersion = 1, rules = emptyList()),
                appliedDurableEventIds = emptySet()
            )
        }
    }

    @Test
    fun reactionCatalogDecodeIgnoresFutureUnknownFields() {
        val decoded = ReactionCatalog.decode(
            """
            {
              "schemaVersion": 1,
              "futureCatalogField": "ignored",
              "rules": [
                {
                  "id": "rainbell-future-safe",
                  "futureRuleField": 17,
                  "requires": {
                    "itemTags": ["rainbell"],
                    "weatherKinds": ["Rain"],
                    "futureRequirement": true
                  },
                  "result": {
                    "visualTags": ["wet"],
                    "futureOutput": "ignored"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("rainbell-future-safe", decoded.rules.single().id)
        assertEquals(listOf("wet"), decoded.rules.single().result.visualTags)
    }

    private fun rule(
        id: String,
        event: DurableReactionEventDefinition
    ) = ReactionRule(
        id = id,
        requires = ReactionRequirements(
            itemTags = listOf("rainbell"),
            weatherKinds = listOf(WeatherEchoKind.Rain)
        ),
        result = ReactionRuleOutput(durableEvents = listOf(event))
    )

    private fun rainEcho() = WeatherEchoSnapshot(
        id = "echo-v1-adversarial-rain",
        kinds = listOf(WeatherEchoKind.Rain),
        rainIntensity = 1,
        snowIntensity = 0,
        windIntensity = 0,
        primaryKind = WeatherEchoKind.Rain
    )

    private fun assertIllegalArgument(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
