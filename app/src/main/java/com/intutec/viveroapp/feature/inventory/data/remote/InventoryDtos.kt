package com.intutec.viveroapp.feature.inventory.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RemoteInventoryDashboardDto(
    val schemaVersion: Int,
    val branchId: String,
    val items: List<RemoteInventoryItemDto>,
    val hasMore: Boolean,
    val nextProductId: String? = null,
)

@Serializable
data class RemoteInventoryItemDto(
    val productId: String,
    val productName: String,
    val productCode: String,
    val productUnit: String,
    val totalQuantity: Double,
    val minimumStock: Double,
    val isLowStock: Boolean,
    val balanceUpdatedAt: String? = null,
)

@Serializable
data class RemoteInventoryOperationDto(
    val schemaVersion: Int,
    val idempotentReplay: Boolean,
    val productId: String,
    val totalQuantity: Double,
    val adjustmentQuantity: Double? = null,
    val movementId: String? = null,
    val countId: String? = null,
    val quantity: Double? = null,
    val previousQuantity: Double? = null,
    val countedQuantity: Double? = null,
)

@Serializable
data class RemoteInventoryHistoryDto(
    val schemaVersion: Int,
    val branchId: String,
    val items: List<RemoteInventoryMovementDto>,
    val hasMore: Boolean,
    val nextCursor: RemoteInventoryHistoryCursorDto? = null,
)

@Serializable
data class RemoteInventoryHistoryCursorDto(val createdAt: String, val id: String)

@Serializable
data class RemoteInventoryMovementDto(
    val id: String,
    val productId: String,
    val productName: String,
    val productCode: String,
    val movementType: String,
    val quantity: Double,
    val notes: String? = null,
    val createdAt: String,
    val createdByLabel: String? = null,
)
