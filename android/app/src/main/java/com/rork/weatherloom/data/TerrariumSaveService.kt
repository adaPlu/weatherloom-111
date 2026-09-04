package com.rork.weatherloom.data

import com.rork.weatherloom.core.terrarium.InventoryEntry
import com.rork.weatherloom.core.terrarium.PlayerInventory
import com.rork.weatherloom.core.terrarium.QuantityMode
import com.rork.weatherloom.core.terrarium.TerrariumCatalog
import com.rork.weatherloom.core.terrarium.TerrariumPlacementRequest
import com.rork.weatherloom.core.terrarium.TerrariumPlacementService

/**
 * Pure save-state bridge for Terrarium ownership and arrangement.
 *
 * It changes only [SaveData] and delegates spatial rules to [TerrariumPlacementService].
 * Persistence remains owned by the repository/SaveStateMutator, so there is still one
 * serialized save document and one read-modify-write gate for the whole game.
 */
class TerrariumSaveService(
    private val catalog: TerrariumCatalog
) {
    private val placementService = TerrariumPlacementService(catalog)

    /** Grants an unlock-style Terrarium entitlement. Repeating the same grant is idempotent. */
    fun grantItem(
        save: SaveData,
        itemId: String,
        variantId: String = "default",
        unlockSource: String? = null
    ): SaveData {
        require(catalog.item(itemId) != null) { "unknown terrarium item $itemId" }
        if (save.terrariumInventory.owns(itemId, variantId)) return save

        val entry = InventoryEntry(
            itemId = itemId,
            variantId = variantId,
            quantityMode = QuantityMode.UnlimitedAfterUnlock,
            quantity = 1,
            unlockSource = unlockSource
        )
        return save.copy(
            terrariumInventory = PlayerInventory(save.terrariumInventory.entries + entry)
        )
    }

    fun placeItem(
        save: SaveData,
        request: TerrariumPlacementRequest
    ): SaveData = save.copy(
        terrariumLayout = placementService.place(
            layout = save.terrariumLayout,
            inventory = save.terrariumInventory,
            request = request
        )
    )

    fun moveItem(
        save: SaveData,
        instanceId: String,
        xNormalized: Float,
        yNormalized: Float,
        depthLayer: Int
    ): SaveData = save.copy(
        terrariumLayout = placementService.move(
            layout = save.terrariumLayout,
            instanceId = instanceId,
            xNormalized = xNormalized,
            yNormalized = yNormalized,
            depthLayer = depthLayer
        )
    )

    /** Stores/removes a placed instance without changing the player's ownership entitlement. */
    fun storeItem(save: SaveData, instanceId: String): SaveData = save.copy(
        terrariumLayout = placementService.remove(save.terrariumLayout, instanceId)
    )
}
