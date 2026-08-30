package com.intutec.viveroapp.feature.catalog.data.remote

import com.intutec.viveroapp.feature.catalog.domain.model.Category

interface CatalogAdminRemoteDataSource {
    suspend fun upsertCategory(name: String, description: String?): RemoteCategoryDto
    suspend fun upsertProduct(
        id: String?,
        internalCode: String,
        barcode: String?,
        commonName: String,
        scientificName: String?,
        description: String,
        categoryId: String,
        priceCents: Long,
        wholesalePriceCents: Long?,
        unit: String,
        minimumStock: Double,
        wateringAdvice: String,
        lightType: String,
        recommendedClimate: String,
        isActive: Boolean,
    ): String

    suspend fun loadCategoriesRaw(): List<RemoteCategoryDto>
}
