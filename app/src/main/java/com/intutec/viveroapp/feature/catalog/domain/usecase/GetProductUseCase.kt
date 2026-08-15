package com.intutec.viveroapp.feature.catalog.domain.usecase

import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import javax.inject.Inject

class GetProductUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(productId: String): Result<Product> = repository.getProduct(productId)
}
