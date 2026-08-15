package com.intutec.viveroapp.feature.catalog.data.remote

import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class RemoteCategoryDto(
    val id: String,
    val name: String,
)

@Serializable
data class RemoteProductDto(
    val id: String,
    @SerialName("internal_code") val internalCode: String,
    val barcode: String? = null,
    @SerialName("common_name") val commonName: String,
    @SerialName("scientific_name") val scientificName: String? = null,
    val description: String = "",
    val category: RemoteCategoryDto,
    @SerialName("price_cents") val priceCents: Long,
    @SerialName("wholesale_price_cents") val wholesalePriceCents: Long? = null,
    val unit: String,
    @SerialName("minimum_stock") val minimumStock: Double = 0.0,
    @SerialName("watering_advice") val wateringAdvice: String = "",
    @SerialName("light_type") val lightType: String = "",
    @SerialName("recommended_climate") val recommendedClimate: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    fun toDomain() = Product(
        id = id,
        internalCode = internalCode,
        barcode = barcode,
        commonName = commonName,
        scientificName = scientificName,
        description = description,
        category = Category(category.id, category.name),
        priceCents = priceCents,
        wholesalePriceCents = wholesalePriceCents,
        unit = unit,
        stockAvailable = 0,
        minimumStock = minimumStock.toInt(),
        imageKey = "monstera",
        wateringAdvice = wateringAdvice,
        lightType = lightType,
        recommendedClimate = recommendedClimate,
        isActive = isActive,
        promotion = null,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        stockKnown = false,
    )
}
