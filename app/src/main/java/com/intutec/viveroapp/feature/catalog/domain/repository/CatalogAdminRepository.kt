package com.intutec.viveroapp.feature.catalog.domain.repository

import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product

data class ProductUpsertRequest(
    val id: String? = null,
    val internalCode: String,
    val barcode: String?,
    val commonName: String,
    val scientificName: String?,
    val description: String,
    val categoryId: String,
    val priceCents: Long,
    val wholesalePriceCents: Long?,
    val unit: String,
    val minimumStock: Int,
    val wateringAdvice: String,
    val lightType: String,
    val recommendedClimate: String,
    val isActive: Boolean,
)

interface CatalogAdminRepository {
    suspend fun loadCategories(): Result<List<Category>>
    suspend fun upsertCategory(name: String, description: String? = null): Result<Category>
    suspend fun upsertProduct(request: ProductUpsertRequest): Result<Product>
    suspend fun uploadAndRegisterImage(productId: String, imageId: String, bytes: ByteArray, mimeType: String): Result<String>
    suspend fun setPrimaryImage(imageId: String): Result<Unit>
}
