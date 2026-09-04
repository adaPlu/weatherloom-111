package com.rork.weatherloom.core.terrarium

/** Input for a deterministic placement operation. The logical footprint is derived from catalog metadata. */
data class TerrariumPlacementRequest(
    val instanceId: String,
    val itemId: String,
    val variantId: String = "default",
    val xNormalized: Float,
    val yNormalized: Float,
    val rotation: TerrariumRotation = TerrariumRotation.Deg0,
    val depthLayer: Int = 0
)

/**
 * Pure domain operations for arranging a Terrarium.
 *
 * Ownership remains in [PlayerInventory], immutable content remains in [TerrariumCatalog],
 * and this service only returns new layout values. That keeps placement deterministic and
 * free of Android/UI/persistence dependencies.
 */
class TerrariumPlacementService(
    private val catalog: TerrariumCatalog
) {

    fun place(
        layout: TerrariumLayout,
        inventory: PlayerInventory,
        request: TerrariumPlacementRequest
    ): TerrariumLayout {
        require(layout.instance(request.instanceId) == null) {
            "placement instanceId ${request.instanceId} already exists"
        }

        val item = catalog.item(request.itemId)
            ?: throw IllegalArgumentException("unknown terrarium item ${request.itemId}")
        val entitlement = inventory.entry(request.itemId, request.variantId)
            ?: throw IllegalArgumentException(
                "item ${request.itemId} variant ${request.variantId} is not owned"
            )

        if (entitlement.quantityMode == QuantityMode.Finite) {
            val placedCount = layout.placements.count {
                it.itemId == request.itemId && it.variantId == request.variantId
            }
            require(placedCount < entitlement.quantity) {
                "all owned copies of ${request.itemId} variant ${request.variantId} are already placed"
            }
        }

        require(request.rotation in item.allowedRotations) {
            "rotation ${request.rotation} is not allowed for ${request.itemId}"
        }

        val placement = TerrariumPlacement(
            instanceId = request.instanceId,
            itemId = request.itemId,
            variantId = request.variantId,
            xNormalized = request.xNormalized,
            yNormalized = request.yNormalized,
            rotation = request.rotation,
            logicalFootprint = item.footprint.rotated(request.rotation),
            depthLayer = request.depthLayer
        )
        val next = TerrariumLayout(layout.placements + placement)
        catalog.requireValid(next)
        return next
    }

    fun move(
        layout: TerrariumLayout,
        instanceId: String,
        xNormalized: Float,
        yNormalized: Float,
        depthLayer: Int
    ): TerrariumLayout {
        val existing = layout.instance(instanceId)
            ?: throw IllegalArgumentException("unknown terrarium placement $instanceId")
        val moved = existing.copy(
            xNormalized = xNormalized,
            yNormalized = yNormalized,
            depthLayer = depthLayer
        )
        val next = TerrariumLayout(
            layout.placements.map { placement ->
                if (placement.instanceId == instanceId) moved else placement
            }
        )
        catalog.requireValid(next)
        return next
    }

    fun remove(layout: TerrariumLayout, instanceId: String): TerrariumLayout {
        require(layout.instance(instanceId) != null) {
            "unknown terrarium placement $instanceId"
        }
        return TerrariumLayout(layout.placements.filterNot { it.instanceId == instanceId })
    }
}
