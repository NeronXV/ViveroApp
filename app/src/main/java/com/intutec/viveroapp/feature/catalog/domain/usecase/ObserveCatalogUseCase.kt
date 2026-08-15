package com.intutec.viveroapp.feature.catalog.domain.usecase

import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCatalogUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    operator fun invoke(): Flow<List<Product>> = repository.observeProducts()
}
