package com.intutec.viveroapp.feature.inventory.domain.model

import java.time.Instant

data class InventoryItem(
    val productId: String,
    val productName: String,
    val productCode: String,
    val productUnit: String,
    val totalQuantity: Int,
    val minimumStock: Int,
    val isLowStock: Boolean,
)

data class InventoryOperationResult(
    val productId: String,
    val totalQuantity: Int,
    val adjustmentQuantity: Int? = null,
    val idempotentReplay: Boolean,
)

data class InventoryMovement(
    val id: String,
    val productId: String,
    val productName: String,
    val productCode: String,
    val movementType: String,
    val quantity: Int,
    val notes: String?,
    val createdAt: Instant,
    val createdByLabel: String?,
)
