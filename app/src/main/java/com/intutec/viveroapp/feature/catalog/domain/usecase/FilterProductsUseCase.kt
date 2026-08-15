package com.intutec.viveroapp.feature.catalog.domain.usecase

import com.intutec.viveroapp.feature.catalog.domain.model.Product
import javax.inject.Inject

class FilterProductsUseCase @Inject constructor() {
    operator fun invoke(
        products: List<Product>,
        query: String,
        categoryId: String?,
        availableOnly: Boolean,
    ): List<Product> {
        val normalizedQuery = query.trim().lowercase()
        return products.filter { product ->
            val matchesQuery = normalizedQuery.isEmpty() ||
                product.commonName.lowercase().contains(normalizedQuery) ||
                product.scientificName?.lowercase()?.contains(normalizedQuery) == true ||
                product.internalCode.lowercase().contains(normalizedQuery) ||
                product.barcode?.contains(normalizedQuery) == true
            val matchesCategory = categoryId == null || product.category.id == categoryId
            val matchesAvailability = !availableOnly || product.isAvailable
            matchesQuery && matchesCategory && matchesAvailability
        }.sortedWith(compareByDescending<Product> { it.isAvailable }.thenBy { it.commonName })
    }
}
