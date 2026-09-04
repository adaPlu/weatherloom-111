package com.rork.weatherloom.core.terrarium

import kotlinx.serialization.Serializable

@Serializable
enum class QuantityMode {
    Finite,
    UnlimitedAfterUnlock
}

@Serializable
data class InventoryEntry(
    val itemId: String,
    val variantId: String = "default",
    val quantityMode: QuantityMode = QuantityMode.UnlimitedAfterUnlock,
    val quantity: Int = 1,
    val unlockSource: String? = null
) {
    init {
        require(itemId.isNotBlank()) { "inventory itemId must not be blank" }
        require(variantId.isNotBlank()) { "inventory variantId must not be blank" }
        require(quantity > 0) { "owned inventory quantity must be positive" }
        unlockSource?.let { require(it.isNotBlank()) { "unlockSource must not be blank" } }
    }

    val stableKey: String get() = "$itemId::$variantId"
}

/** Owns placement entitlement only; it never stores coordinates or growth. */
@Serializable
data class PlayerInventory(
    val entries: List<InventoryEntry> = emptyList()
) {
    init {
        val keys = entries.map { it.stableKey }
        require(keys.size == keys.distinct().size) { "inventory ownership rows must be unique" }
    }

    fun owns(itemId: String, variantId: String = "default"): Boolean =
        entries.any { it.itemId == itemId && it.variantId == variantId }

    fun entry(itemId: String, variantId: String = "default"): InventoryEntry? =
        entries.firstOrNull { it.itemId == itemId && it.variantId == variantId }
}
