package com.intutec.viveroapp.feature.scanner.domain.usecase

import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class FindProductByCodeUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(code: String): Result<Product?> {
        val normalized = code.trim()
        if (normalized.isBlank()) return Result.failure(IllegalArgumentException("Ingresa un código válido."))
        return repository.findProductByCode(normalized).also { result ->
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
        }
    }
}
