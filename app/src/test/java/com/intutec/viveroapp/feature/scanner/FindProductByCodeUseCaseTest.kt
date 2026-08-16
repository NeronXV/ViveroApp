package com.intutec.viveroapp.feature.scanner

import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.model.CatalogSnapshot
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import com.intutec.viveroapp.feature.scanner.domain.usecase.FindProductByCodeUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindProductByCodeUseCaseTest {
    @Test
    fun `normalizes code before querying repository`() = runTest {
        var received = ""
        val useCase = FindProductByCodeUseCase(object : CatalogRepository {
            override fun observeCatalog(): Flow<CatalogSnapshot> = emptyFlow()
            override suspend fun getProduct(productId: String): Result<Product> = Result.failure(NotImplementedError())
            override suspend fun findProductByCode(code: String): Result<Product?> {
                received = code
                return Result.success(null)
            }
        })

        assertTrue(useCase("  PL-001  ").isSuccess)
        assertEquals("PL-001", received)
    }

    @Test
    fun `blank code fails without querying repository`() = runTest {
        var queried = false
        val useCase = FindProductByCodeUseCase(object : CatalogRepository {
            override fun observeCatalog(): Flow<CatalogSnapshot> = emptyFlow()
            override suspend fun getProduct(productId: String): Result<Product> = Result.failure(NotImplementedError())
            override suspend fun findProductByCode(code: String): Result<Product?> {
                queried = true
                return Result.success(null)
            }
        })

        assertTrue(useCase("   ").isFailure)
        assertFalse(queried)
    }
}
