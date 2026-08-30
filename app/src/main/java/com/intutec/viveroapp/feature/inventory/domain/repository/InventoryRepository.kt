package com.intutec.viveroapp.feature.inventory.domain.repository

import com.intutec.viveroapp.feature.inventory.domain.model.InventoryItem
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryMovement
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryOperationResult

interface InventoryRepository {
    suspend fun getDashboard(): Result<List<InventoryItem>>
    suspend fun recordReception(
        productId: String,
        quantity: Int,
        notes: String?,
        idempotencyKey: String,
    ): Result<InventoryOperationResult>

    suspend fun reconcileCount(
        productId: String,
        countedQuantity: Int,
        reason: String,
        idempotencyKey: String,
    ): Result<InventoryOperationResult>

    suspend fun getHistory(productId: String): Result<List<InventoryMovement>>
}
