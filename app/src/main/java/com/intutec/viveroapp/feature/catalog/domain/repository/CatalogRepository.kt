package com.intutec.viveroapp.feature.catalog.domain.repository

import com.intutec.viveroapp.feature.catalog.domain.model.CatalogSnapshot
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observeCatalog(): Flow<CatalogSnapshot>
    suspend fun getProduct(productId: String): Result<Product>
    suspend fun findProductByCode(code: String): Result<Product?>
}
