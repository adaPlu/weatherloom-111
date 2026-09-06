package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.GrowthProfile
import com.rork.weatherloom.core.terrarium.GrowthStage
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumCategory
import com.rork.weatherloom.core.terrarium.TerrariumFootprint
import com.rork.weatherloom.core.terrarium.TerrariumItem
import com.rork.weatherloom.core.terrarium.TerrariumLayout
import com.rork.weatherloom.core.terrarium.TerrariumPlacement
import com.rork.weatherloom.core.terrarium.TerrariumRotation
import com.rork.weatherloom.core.terrarium.reaction.DurableReactionEventDefinition
import com.rork.weatherloom.core.terrarium.reaction.DurableReactionEventKind
import com.rork.weatherloom.core.terrarium.reaction.EnvironmentState
import com.rork.weatherloom.core.terrarium.reaction.ReactionCatalog
import com.rork.weatherloom.core.terrarium.reaction.ReactionRequirements
import com.rork.weatherloom.core.terrarium.reaction.ReactionRule
import com.rork.weatherloom.core.terrarium.reaction.ReactionRuleOutput
import com.rork.weatherloom.core.weather.WeatherEchoKind
import com.rork.weatherloom.core.weather.WeatherEchoSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitorDiscoveryAdversarialTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val catalog = TerrariumCatalog(
        schemaVersion = 1,
        growthProfiles = listOf(
            GrowthProfile(
                id = "botanical",
                stages = listOf(GrowthStage.Seed, GrowthStage.Sprout, GrowthStage.Bloom),
                pulsesPerStage = 1
            )
        ),
        items = listOf(
            TerrariumItem(
                id = "rainbell",
                nameKey = "terrarium.item.rainbell",
                category = TerrariumCategory.Botanical,
                visualFamily = "bell-flower",
                footprint = TerrariumFootprint(1, 1),
                allowedRotations = listOf(TerrariumRotation.Deg0),
                reactionTags = listOf("plant", "rainbell", "rain"),
                growthProfileId = "botanical"
            )
        )
    )
    private val discoveryDefinition = DurableReactionEventDefinition(
        id = "discovery.rainbell_after_rain",
        kind = DurableReactionEventKind.DiscoveryCandidate,
        payloadId = "rainbell_after_rain"
    )
    private val reactions = ReactionCatalog(
        schemaVersion = 1,
        rules = listOf(
            ReactionRule(
                id = "rainbell-after-rain",
                requires = ReactionRequirements(
                    itemTags = listOf("rainbell"),
                    weatherKinds = listOf(WeatherEchoKind.Rain)
                ),
                result = ReactionRuleOutput(
                    visualTags = listOf("bloom", "wet"),
                    durableEvents = listOf(discoveryDefinition),
                    visitorIds = listOf("butterfly")
                )
            )
        )
    )

    @Test
    fun legacyAppliedDiscoveryEventBackfillsRegistryWithoutRegranting() {
        val service = TerrariumReactionSaveService(catalog, reactions)
        val save = rainySave().copy(
            appliedTerrariumReactionEventIds = listOf("discovery.rainbell_after_rain")
        )

        val result = service.evaluateAndApply(save)

        assertEquals(listOf("rainbell_after_rain"), result.save.terrariumDiscoveries)
        assertEquals(
            listOf("discovery.rainbell_after_rain"),
            result.save.appliedTerrariumReactionEventIds
        )
        assertTrue(result.newlyAppliedDurableEvents.isEmpty())
        assertEquals(listOf("butterfly"), result.reactions.visitors.map { it.visitorId })
    }

    @Test
    fun visitorIdsRejectInvalidDuplicateAndNonCanonicalContent() {
        assertIllegalArgument { ReactionRuleOutput(visitorIds = listOf("Butterfly")) }
        assertIllegalArgument { ReactionRuleOutput(visitorIds = listOf("butterfly", "butterfly")) }
        assertIllegalArgument { ReactionRuleOutput(visitorIds = listOf("moth", "butterfly")) }

        val valid = ReactionRuleOutput(visitorIds = listOf("butterfly", "moth"))
        assertEquals(listOf("butterfly", "moth"), valid.visitorIds)
    }

    @Test
    fun schemaFiveMigrationPreservesOldReactionLedgerForCatalogBackfill() {
        val raw = """
            {
              "schema": 5,
              "tutorialSeen": true,
              "reducedMotion": true,
              "appliedTerrariumReactionEventIds": ["discovery.rainbell_after_rain"]
            }
        """.trimIndent()

        val migrated = SaveMigration.decode(raw, json)

        assertEquals(CURRENT_SAVE_SCHEMA, migrated.schema)
        assertTrue(migrated.tutorialSeen)
        assertTrue(migrated.reducedMotion)
        assertEquals(
            listOf("discovery.rainbell_after_rain"),
            migrated.appliedTerrariumReactionEventIds
        )
        assertTrue(migrated.terrariumDiscoveries.isEmpty())
        assertFalse(migrated.appliedTerrariumReactionEventIds.isEmpty())
    }

    private fun rainySave() = SaveData(
        terrariumLayout = TerrariumLayout(
            placements = listOf(
                TerrariumPlacement(
                    instanceId = "rainbell-001",
                    itemId = "rainbell",
                    xNormalized = 0.5f,
                    yNormalized = 0.6f,
                    rotation = TerrariumRotation.Deg0,
                    logicalFootprint = TerrariumFootprint(1, 1)
                )
            )
        ),
        terrariumEnvironment = EnvironmentState(
            weatherEcho = WeatherEchoSnapshot(
                id = "echo-legacy-discovery",
                kinds = listOf(WeatherEchoKind.Rain),
                rainIntensity = 1,
                snowIntensity = 0,
                windIntensity = 0,
                primaryKind = WeatherEchoKind.Rain
            )
        )
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
