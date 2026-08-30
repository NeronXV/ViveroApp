package com.intutec.viveroapp.feature.inventory.data.repository

import com.intutec.viveroapp.core.network.SupabaseProvider
import com.intutec.viveroapp.feature.inventory.data.remote.RemoteInventoryDashboardDto
import com.intutec.viveroapp.feature.inventory.data.remote.RemoteInventoryHistoryDto
import com.intutec.viveroapp.feature.inventory.data.remote.RemoteInventoryItemDto
import com.intutec.viveroapp.feature.inventory.data.remote.RemoteInventoryMovementDto
import com.intutec.viveroapp.feature.inventory.data.remote.RemoteInventoryOperationDto
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryItem
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryMovement
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryOperationResult
import com.intutec.viveroapp.feature.inventory.domain.repository.InventoryRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class SupabaseInventoryRepository @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : InventoryRepository {
    override suspend fun getDashboard(): Result<List<InventoryItem>> = inventoryCall {
        val allItems = mutableListOf<InventoryItem>()
        var cursor: String? = null
        var branchId: String? = null
        do {
            val page: RemoteInventoryDashboardDto = rpc(
                "get_my_inventory_dashboard",
                JsonObject(mapOf(
                    "p_limit" to JsonPrimitive(PAGE_SIZE),
                    "p_after_product_id" to (cursor?.let(::JsonPrimitive) ?: JsonNull),
                )),
            )
            requireVersion(page.schemaVersion)
            val pageBranchId = page.branchId.requireUuid()
            check(branchId == null || branchId == pageBranchId) { "Inventario cambió de sucursal durante la consulta." }
            branchId = pageBranchId
            allItems += page.items.map(RemoteInventoryItemDto::toDomain)
            val next = page.nextProductId
            check(!page.hasMore || !next.isNullOrBlank()) { "Inventario devolvió una página incompleta." }
            check(next == null || next != cursor) { "Inventario devolvió un cursor repetido." }
            cursor = next
        } while (page.hasMore)
        check(allItems.map(InventoryItem::productId).distinct().size == allItems.size) {
            "Inventario devolvió productos duplicados."
        }
        allItems.sortedBy { it.productName.lowercase() }
    }

    override suspend fun recordReception(
        productId: String,
        quantity: Int,
        notes: String?,
        idempotencyKey: String,
    ): Result<InventoryOperationResult> = inventoryCall {
        require(quantity > 0) { "La recepción debe ser mayor que cero." }
        rpc<RemoteInventoryOperationDto>(
            "record_inventory_reception",
            JsonObject(mapOf(
                "p_product_id" to JsonPrimitive(productId.requireUuid()),
                "p_quantity" to JsonPrimitive(quantity),
                "p_notes" to (notes?.trim()?.takeIf(String::isNotEmpty)?.let(::JsonPrimitive) ?: JsonNull),
                "p_idempotency_key" to JsonPrimitive(idempotencyKey.requireUuid()),
            )),
        ).toDomain()
    }

    override suspend fun reconcileCount(
        productId: String,
        countedQuantity: Int,
        reason: String,
        idempotencyKey: String,
    ): Result<InventoryOperationResult> = inventoryCall {
        require(countedQuantity >= 0) { "El conteo no puede ser negativo." }
        require(reason.trim().length in 3..240) { "Escribe un motivo de 3 a 240 caracteres." }
        rpc<RemoteInventoryOperationDto>(
            "reconcile_inventory_count",
            JsonObject(mapOf(
                "p_product_id" to JsonPrimitive(productId.requireUuid()),
                "p_counted_quantity" to JsonPrimitive(countedQuantity),
                "p_reason" to JsonPrimitive(reason.trim()),
                "p_idempotency_key" to JsonPrimitive(idempotencyKey.requireUuid()),
                "p_location_id" to JsonNull,
            )),
        ).toDomain()
    }

    override suspend fun getHistory(productId: String): Result<List<InventoryMovement>> = inventoryCall {
        val response: RemoteInventoryHistoryDto = rpc(
            "get_my_inventory_history",
            JsonObject(mapOf(
                "p_product_id" to JsonPrimitive(productId.requireUuid()),
                "p_limit" to JsonPrimitive(50),
                "p_after_created_at" to JsonNull,
                "p_after_id" to JsonNull,
            )),
        )
        requireVersion(response.schemaVersion)
        response.branchId.requireUuid()
        check(!response.hasMore || response.nextCursor != null) { "Inventario devolvió un historial incompleto." }
        response.items.map(RemoteInventoryMovementDto::toDomain)
    }

    private suspend inline fun <reified T> rpc(function: String, parameters: JsonObject): T {
        val client = checkNotNull(supabaseProvider.client) { "Supabase no está configurado para Inventario." }
        val response = client.postgrest.rpc(function = function, parameters = parameters)
        return inventoryJson.decodeFromString(response.data)
    }

    private companion object {
        const val PAGE_SIZE = 100
        val inventoryJson = Json { ignoreUnknownKeys = false }
    }
}

private inline fun <T> inventoryCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: IllegalArgumentException) {
    Result.failure(error)
} catch (_: Throwable) {
    Result.failure(IllegalStateException("No pudimos completar la operación de inventario. Intenta nuevamente."))
}

private fun requireVersion(version: Int) = check(version == 1) {
    "Inventario devolvió una versión no compatible."
}

private fun RemoteInventoryItemDto.toDomain(): InventoryItem {
    val total = totalQuantity.wholeQuantity("existencia")
    val minimum = minimumStock.wholeQuantity("mínimo")
    check(productName.isNotBlank() && productCode.isNotBlank() && productUnit.isNotBlank()) {
        "Inventario devolvió un producto incompleto."
    }
    check(isLowStock == (total <= minimum)) { "Inventario devolvió una alerta inconsistente." }
    return InventoryItem(productId.requireUuid(), productName.trim(), productCode.trim(), productUnit.trim(), total, minimum, isLowStock)
}

private fun RemoteInventoryOperationDto.toDomain(): InventoryOperationResult {
    requireVersion(schemaVersion)
    check(!movementId.isNullOrBlank() || !countId.isNullOrBlank()) { "Inventario devolvió una operación incompleta." }
    return InventoryOperationResult(
        productId = productId.requireUuid(),
        totalQuantity = totalQuantity.wholeQuantity("existencia"),
        adjustmentQuantity = adjustmentQuantity?.wholeSignedQuantity("ajuste"),
        idempotentReplay = idempotentReplay,
    )
}

private fun RemoteInventoryMovementDto.toDomain() = InventoryMovement(
    id = id.requireUuid(),
    productId = productId.requireUuid(),
    productName = productName.trim().also { check(it.isNotEmpty()) },
    productCode = productCode.trim().also { check(it.isNotEmpty()) },
    movementType = movementType,
    quantity = quantity.wholeSignedQuantity("movimiento"),
    notes = notes?.trim()?.takeIf(String::isNotEmpty),
    createdAt = OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant(),
    createdByLabel = createdByLabel?.trim()?.takeIf(String::isNotEmpty),
)

private fun Double.wholeQuantity(field: String): Int {
    check(isFinite() && this >= 0 && this % 1.0 == 0.0 && this <= Int.MAX_VALUE) {
        "Inventario devolvió un $field inválido."
    }
    return toInt()
}

private fun Double.wholeSignedQuantity(field: String): Int {
    check(isFinite() && this % 1.0 == 0.0 && this in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
        "Inventario devolvió un $field inválido."
    }
    return toInt()
}

private fun String.requireUuid(): String = UUID.fromString(this).toString()
