package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.TerrariumCatalog

/** Result of translating a puzzle completion reward into durable Terrarium ownership. */
data class PuzzleRewardGrantResult(
    val save: SaveData,
    val newTerrariumItem: Boolean,
    val terrariumItemId: String? = null
)

/**
 * Pure bridge from authored puzzle reward IDs to the Terrarium inventory domain.
 *
 * Level content may outlive or temporarily disagree with the Terrarium catalog during
 * migrations/content rollouts, so unknown reward IDs are ignored rather than making a
 * player's entire save unreadable. Valid Terrarium rewards are granted idempotently.
 */
class PuzzleRewardBridge(
    private val catalog: TerrariumCatalog
) {
    private val terrariumSaveService = TerrariumSaveService(catalog)

    fun grant(
        save: SaveData,
        levelId: String,
        rewardId: String?
    ): PuzzleRewardGrantResult {
        if (rewardId == null || catalog.item(rewardId) == null) {
            return PuzzleRewardGrantResult(
                save = save,
                newTerrariumItem = false,
                terrariumItemId = null
            )
        }

        if (save.terrariumInventory.owns(rewardId)) {
            return PuzzleRewardGrantResult(
                save = save,
                newTerrariumItem = false,
                terrariumItemId = rewardId
            )
        }

        val next = terrariumSaveService.grantItem(
            save = save,
            itemId = rewardId,
            unlockSource = "level:$levelId"
        )
        return PuzzleRewardGrantResult(
            save = next,
            newTerrariumItem = true,
            terrariumItemId = rewardId
        )
    }
}
