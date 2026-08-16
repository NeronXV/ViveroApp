package com.intutec.viveroapp.feature.cart.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

data class SaleSyncRequest(
    val saleId: String,
    val folio: String,
    val items: List<SaleSyncItem>,
)

data class SaleSyncItem(val productId: String, val quantity: Int)

@Serializable
data class SaleSyncResponse(
    val id: String,
    val folio: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("branch_id") val branchId: String,
    val status: String,
)

enum class SaleSyncFailureType { TEMPORARY, PERMANENT }

class SaleSyncRemoteException(
    val type: SaleSyncFailureType,
    message: String,
) : IllegalStateException(message)

interface SaleSyncRemoteDataSource {
    suspend fun submitSale(request: SaleSyncRequest): SaleSyncResponse
}

private val saleSyncResponseJson = Json { ignoreUnknownKeys = true }

internal fun decodeSaleSyncResponse(data: String): SaleSyncResponse {
    val root = saleSyncResponseJson.parseToJsonElement(data)
    val record = when (root) {
        is JsonObject -> root
        is JsonArray -> {
            check(root.size == 1) { "Supabase devolvió una cantidad inesperada de ventas." }
            root.single() as? JsonObject
                ?: error("Supabase devolvió una venta con formato inesperado.")
        }
        else -> error("Supabase devolvió una venta con formato inesperado.")
    }
    return saleSyncResponseJson.decodeFromJsonElement(record)
}

internal fun SaleSyncRequest.toRpcParameters(): JsonObject = JsonObject(
    mapOf(
        "p_sale_id" to JsonPrimitive(saleId),
        "p_folio" to JsonPrimitive(folio),
        "p_items" to JsonArray(
            items.map { item ->
                JsonObject(
                    mapOf(
                        "product_id" to JsonPrimitive(item.productId),
                        "quantity" to JsonPrimitive(item.quantity),
                    ),
                )
            },
        ),
        "p_customer_id" to JsonNull,
    ),
)
