package com.intutec.viveroapp.feature.catalog.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteCategoryDto(
    val id: String,
    val name: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class RemoteProductImageDto(
    val id: String,
    @SerialName("product_id") val productId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("alt_text") val altText: String? = null,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("is_primary") val isPrimary: Boolean,
)

@Serializable
data class RemoteProductDto(
    val id: String,
    @SerialName("internal_code") val internalCode: String,
    val barcode: String? = null,
    @SerialName("common_name") val commonName: String,
    @SerialName("scientific_name") val scientificName: String? = null,
    val description: String = "",
    @SerialName("category_id") val categoryId: String,
    @SerialName("price_cents") val priceCents: Long,
    @SerialName("wholesale_price_cents") val wholesalePriceCents: Long? = null,
    val unit: String,
    @SerialName("minimum_stock") val minimumStock: Double = 0.0,
    @SerialName("watering_advice") val wateringAdvice: String = "",
    @SerialName("light_type") val lightType: String = "",
    @SerialName("recommended_climate") val recommendedClimate: String = "",
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val images: List<RemoteProductImageDto> = emptyList(),
)

@Serializable
data class RemoteBranchCatalogInventoryItemDto(
    val productId: String,
    val totalQuantity: Double,
    val updatedAt: String? = null,
)

@Serializable
data class RemoteBranchCatalogInventoryDto(
    val schemaVersion: Int,
    val branchId: String,
    val items: List<RemoteBranchCatalogInventoryItemDto>,
)
