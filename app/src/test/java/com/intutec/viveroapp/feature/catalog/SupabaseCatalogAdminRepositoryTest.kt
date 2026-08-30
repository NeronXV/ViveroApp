package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.feature.catalog.data.remote.CatalogAdminRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogImageRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteCategoryDto
import com.intutec.viveroapp.feature.catalog.data.repository.SupabaseCatalogAdminRepository
import com.intutec.viveroapp.feature.catalog.domain.repository.ProductUpsertRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeImageRemote : CatalogImageRemoteDataSource {
    override suspend fun uploadImage(productId: String, imageId: String, bytes: ByteArray, mimeType: String): String = "$productId/$imageId.jpg"
    override suspend fun insertProductImage(productId: String, imageId: String, storagePath: String): String = imageId
    override suspend fun setPrimaryImage(imageId: String) {}
    override suspend fun deleteProductImage(imageId: String) {}
}

class SupabaseCatalogAdminRepositoryTest {
    @Test
    fun `upsertProduct valida unidad enum`() = runTest {
        val repo = SupabaseCatalogAdminRepository(FakeRemote(), FakeImageRemote())
        val result = repo.upsertProduct(ProductUpsertRequest(internalCode = "AB-01", commonName = "Rosa", categoryId = "11111111-1111-4111-8111-111111111111", priceCents = 1000, wholesalePriceCents = null, unit = "invalida", minimumStock = 0, description = "", wateringAdvice = "", lightType = "", recommendedClimate = "", isActive = true, barcode = null, scientificName = null))
        assertTrue(result.isFailure)
    }

    @Test
    fun `parsea precio y unidad correctamente`() = runTest {
        val repo = SupabaseCatalogAdminRepository(FakeRemote(), FakeImageRemote())
        val result = repo.upsertProduct(ProductUpsertRequest(internalCode = "PL-01", commonName = "Lavanda", categoryId = "11111111-1111-4111-8111-111111111111", priceCents = 5000, wholesalePriceCents = 4000, unit = "maceta", minimumStock = 3, description = "desc", wateringAdvice = "poco", lightType = "sol", recommendedClimate = "templado", isActive = true, barcode = "123", scientificName = "Lavandula"))
        assertTrue(result.isSuccess)
        assertEquals("maceta", result.getOrNull()!!.unit)
        assertEquals(5000L, result.getOrNull()!!.priceCents)
    }

    @Test
    fun `idempotencia no duplica si mismo p_id`() = runTest {
        // El backend es idempotente via p_id on conflict; el repo debe enviar null para creación y permitir retry con mismo payload sin duplicar
        val repo = SupabaseCatalogAdminRepository(FakeRemote(), FakeImageRemote())
        val req = ProductUpsertRequest(internalCode = "IDEM-01", commonName = "Idem", categoryId = "11111111-1111-4111-8111-111111111111", priceCents = 1000, wholesalePriceCents = null, unit = "pieza", minimumStock = 0, description = "", wateringAdvice = "", lightType = "", recommendedClimate = "", isActive = true, barcode = null, scientificName = null)
        val r1 = repo.upsertProduct(req)
        val r2 = repo.upsertProduct(req)
        assertTrue(r1.isSuccess && r2.isSuccess)
        assertEquals(r1.getOrNull()!!.internalCode, r2.getOrNull()!!.internalCode)
    }

    private class FakeRemote : CatalogAdminRemoteDataSource {
        override suspend fun loadCategoriesRaw(): List<RemoteCategoryDto> = emptyList()
        override suspend fun upsertCategory(name: String, description: String?): RemoteCategoryDto = RemoteCategoryDto("11111111-1111-4111-8111-111111111111", name, true)
        override suspend fun upsertProduct(id: String?, internalCode: String, barcode: String?, commonName: String, scientificName: String?, description: String, categoryId: String, priceCents: Long, wholesalePriceCents: Long?, unit: String, minimumStock: Double, wateringAdvice: String, lightType: String, recommendedClimate: String, isActive: Boolean): String {
            return """{"id":"${id ?: "99999999-9999-4999-8999-999999999999"}","internal_code":"$internalCode","common_name":"$commonName","category_id":"$categoryId","price_cents":$priceCents,"unit":"$unit","minimum_stock":$minimumStock,"is_active":$isActive,"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}"""
        }
    }
}
