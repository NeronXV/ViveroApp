package com.intutec.viveroapp.feature.catalog.domain.model

import java.time.Instant

data class Product(
    val id: String,
    val internalCode: String,
    val barcode: String?,
    val commonName: String,
    val scientificName: String?,
    val description: String,
    val category: Category,
    val priceCents: Long,
    val wholesalePriceCents: Long?,
    val unit: String,
    val stockAvailable: Int,
    val minimumStock: Int,
    val imageKey: String,
    val wateringAdvice: String,
    val lightType: String,
    val recommendedClimate: String,
    val isActive: Boolean,
    val promotion: ProductPromotion?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val stockKnown: Boolean = true,
    val images: List<ProductImage> = emptyList(),
) {
    val isAvailable: Boolean get() = isActive && stockAvailable > 0
    val effectivePriceCents: Long get() = promotion?.priceCents ?: priceCents
    val primaryImage: ProductImage? get() = images.firstOrNull()
}

data class ProductImage(
    val id: String,
    val productId: String,
    val storagePath: String,
    val altText: String?,
    val sortOrder: Int,
    val isPrimary: Boolean,
)

data class ProductPromotion(
    val name: String,
    val priceCents: Long,
)
