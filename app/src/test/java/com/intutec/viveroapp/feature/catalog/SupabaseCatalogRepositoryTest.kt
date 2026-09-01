package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteBranchCatalogInventoryDto
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteBranchCatalogInventoryItemDto
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteCatalogPricingDto
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteCatalogPricingItemDto
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteCatalogPromotionDto
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteCategoryDto
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteProductDto
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteProductImageDto
import com.intutec.viveroapp.feature.catalog.data.repository.FakeCatalogRepository
import com.intutec.viveroapp.feature.catalog.data.repository.SessionCatalogRepository
import com.intutec.viveroapp.feature.catalog.data.repository.SupabaseCatalogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseCatalogRepositoryTest {
    @Test
    fun `maps real UUIDs and preserves integer cents`() = runTest {
        val remote = remote(products = listOf(product(priceCents = 123_456_789L)))

        val mapped = SupabaseCatalogRepository(remote).observeCatalog().first().products.single()

        assertEquals(PRODUCT_ID, mapped.id)
        assertEquals(CATEGORY_ID, mapped.category.id)
        assertEquals(123_456_789L, mapped.priceCents)
        assertFalse(mapped.stockKnown)
        assertEquals(0, mapped.stockAvailable)
        assertEquals("", mapped.imageKey)
    }

    @Test
    fun `maps authoritative catalog promotion from pricing RPC`() = runTest {
        val remote = remote(
            pricing = RemoteCatalogPricingDto(
                schemaVersion = 1,
                items = listOf(
                    RemoteCatalogPricingItemDto(
                        productId = PRODUCT_ID,
                        listPriceCents = 58_900L,
                        effectivePriceCents = 47_120L,
                        activePromotion = RemoteCatalogPromotionDto(
                            id = PROMOTION_ID,
                            name = "Campaña de temporada",
                            discountPercent = 20.0,
                        ),
                    ),
                ),
            ),
        )

        val mapped = SupabaseCatalogRepository(remote).observeCatalog().first().products.single()

        assertEquals(58_900L, mapped.priceCents)
        assertEquals(47_120L, mapped.effectivePriceCents)
        assertEquals("Campaña de temporada", mapped.promotion?.name)
    }

    @Test
    fun `catalog timestamps accept PostgreSQL ISO 8601 representations`() = runTest {
        val cases = mapOf(
            "2026-08-16T04:04:38Z" to Instant.parse("2026-08-16T04:04:38Z"),
            "2026-08-16T04:04:38+00:00" to Instant.parse("2026-08-16T04:04:38Z"),
            "2026-08-16T04:04:38.071382+00:00" to Instant.parse("2026-08-16T04:04:38.071382Z"),
            "2026-08-15T21:04:38.071382-07:00" to Instant.parse("2026-08-16T04:04:38.071382Z"),
        )

        cases.forEach { (timestamp, expected) ->
            val remote = remote(products = listOf(product(createdAt = timestamp, updatedAt = timestamp)))
            val mapped = SupabaseCatalogRepository(remote).observeCatalog().first().products.single()

            assertEquals(expected, mapped.createdAt)
            assertEquals(expected, mapped.updatedAt)
        }
    }

    @Test
    fun `active product selects marked primary image`() = runTest {
        val secondary = image(id = IMAGE_ID_1, sortOrder = 0, isPrimary = false)
        val primary = image(id = IMAGE_ID_2, sortOrder = 8, isPrimary = true)
        val remote = remote(products = listOf(product(images = listOf(secondary, primary))))

        val mapped = SupabaseCatalogRepository(remote).observeCatalog().first().products.single()

        assertEquals(IMAGE_ID_2, mapped.primaryImage?.id)
        assertEquals("products/primary.jpg", mapped.primaryImage?.storagePath)
    }

    @Test
    fun `active product without images keeps nullable primary image`() = runTest {
        val remote = remote(products = listOf(product(images = emptyList())))

        val mapped = SupabaseCatalogRepository(remote).observeCatalog().first().products.single()

        assertTrue(mapped.images.isEmpty())
        assertNull(mapped.primaryImage)
    }

    @Test
    fun `images use deterministic primary order sort order and UUID`() = runTest {
        val images = listOf(
            image(id = IMAGE_ID_3, sortOrder = 1, isPrimary = false),
            image(id = IMAGE_ID_2, sortOrder = 5, isPrimary = true),
            image(id = IMAGE_ID_1, sortOrder = 1, isPrimary = false),
        )
        val remote = remote(products = listOf(product(images = images)))

        val mappedIds = SupabaseCatalogRepository(remote).observeCatalog().first()
            .products.single().images.map { it.id }

        assertEquals(listOf(IMAGE_ID_2, IMAGE_ID_1, IMAGE_ID_3), mappedIds)
    }

    @Test
    fun `empty remote catalog is a successful empty snapshot`() = runTest {
        val remote = remote(categories = emptyList(), products = emptyList())

        val snapshot = SupabaseCatalogRepository(remote).observeCatalog().first()

        assertTrue(snapshot.categories.isEmpty())
        assertTrue(snapshot.products.isEmpty())
        assertEquals(4, remote.readCalls)
    }

    @Test
    fun `remote error is propagated and never converted to empty`() = runTest {
        val remote = remote()
        remote.failure = IllegalStateException("network unavailable")

        val result = runCatching { SupabaseCatalogRepository(remote).observeCatalog().first() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("network") == true)
    }

    @Test
    fun `invalid remote UUID rejects complete catalog`() = runTest {
        val remote = remote(products = listOf(product(id = "not-a-uuid")))

        val result = runCatching { SupabaseCatalogRepository(remote).observeCatalog().first() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("product id inválido") == true)
    }

    @Test
    fun `REMOTE session never receives demo products`() = runTest {
        val remoteData = remote(categories = emptyList(), products = emptyList())
        val store = SessionStore().apply { update(session(SessionMode.REMOTE)) }
        val repository = SessionCatalogRepository(
            sessionStore = store,
            remote = SupabaseCatalogRepository(remoteData),
            demo = FakeCatalogRepository(),
        )

        val snapshot = repository.observeCatalog().first()

        assertTrue(snapshot.products.isEmpty())
        assertEquals(4, remoteData.readCalls)
    }

    @Test
    fun `DEMO session preserves simulated catalog without remote reads`() = runTest {
        val remoteData = remote()
        val store = SessionStore().apply { update(session(SessionMode.DEMO)) }
        val repository = SessionCatalogRepository(
            sessionStore = store,
            remote = SupabaseCatalogRepository(remoteData),
            demo = FakeCatalogRepository(),
        )

        val snapshot = repository.observeCatalog().first()

        assertTrue(snapshot.products.any { it.id == "monstera" })
        assertTrue(snapshot.products.any { it.id == "lavanda" })
        assertEquals(0, remoteData.readCalls)
    }

    @Test
    fun `remote catalog contract exposes read operations only`() {
        val operations = CatalogRemoteDataSource::class.java.declaredMethods
            .map { it.name.substringBefore('-') }
            .toSet()

        assertEquals(
            setOf(
                "loadVisibleCategories",
                "loadActiveProducts",
                "loadCatalogPricing",
                "loadMyBranchInventory",
            ),
            operations,
        )
    }

    @Test
    fun `branch inventory makes stock authoritative for matching products`() = runTest {
        val remote = remote(
            inventory = RemoteBranchCatalogInventoryDto(
                schemaVersion = 1,
                branchId = BRANCH_ID,
                items = listOf(
                    RemoteBranchCatalogInventoryItemDto(PRODUCT_ID, 7.0),
                ),
            ),
        )

        val mapped = SupabaseCatalogRepository(remote).observeCatalog().first().products.single()

        assertTrue(mapped.stockKnown)
        assertEquals(7, mapped.stockAvailable)
        assertTrue(mapped.isAvailable)
    }

    @Test
    fun `missing inventory RPC preserves catalog with explicitly unknown stock`() = runTest {
        val mapped = SupabaseCatalogRepository(remote(inventory = null))
            .observeCatalog().first().products.single()

        assertFalse(mapped.stockKnown)
        assertEquals(0, mapped.stockAvailable)
    }

    @Test
    fun `fractional inventory is rejected while Android cart supports whole quantities only`() = runTest {
        val remote = remote(
            inventory = RemoteBranchCatalogInventoryDto(
                schemaVersion = 1,
                branchId = BRANCH_ID,
                items = listOf(RemoteBranchCatalogInventoryItemDto(PRODUCT_ID, 1.5)),
            ),
        )

        val result = runCatching { SupabaseCatalogRepository(remote).observeCatalog().first() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("cantidades enteras") == true)
    }

    private fun remote(
        categories: List<RemoteCategoryDto> = listOf(category()),
        products: List<RemoteProductDto> = listOf(product()),
        pricing: RemoteCatalogPricingDto = pricingFor(products),
        inventory: RemoteBranchCatalogInventoryDto? = null,
    ) = RecordingRemoteDataSource(categories, products, pricing, inventory)

    private class RecordingRemoteDataSource(
        private val categories: List<RemoteCategoryDto>,
        private val products: List<RemoteProductDto>,
        private val pricing: RemoteCatalogPricingDto,
        private val inventory: RemoteBranchCatalogInventoryDto?,
    ) : CatalogRemoteDataSource {
        var readCalls = 0
        var failure: Throwable? = null

        override suspend fun loadVisibleCategories(): List<RemoteCategoryDto> {
            readCalls += 1
            failure?.let { throw it }
            return categories
        }

        override suspend fun loadActiveProducts(): List<RemoteProductDto> {
            readCalls += 1
            failure?.let { throw it }
            return products
        }

        override suspend fun loadCatalogPricing(): RemoteCatalogPricingDto {
            readCalls += 1
            failure?.let { throw it }
            return pricing
        }

        override suspend fun loadMyBranchInventory(): RemoteBranchCatalogInventoryDto? {
            readCalls += 1
            failure?.let { throw it }
            return inventory
        }
    }

    companion object {
        private const val CATEGORY_ID = "11111111-1111-4111-8111-111111111111"
        private const val PRODUCT_ID = "22222222-2222-4222-8222-222222222222"
        private const val PROMOTION_ID = "66666666-6666-4666-8666-666666666666"
        private const val BRANCH_ID = "55555555-5555-4555-8555-555555555555"
        private const val IMAGE_ID_1 = "33333333-3333-4333-8333-333333333331"
        private const val IMAGE_ID_2 = "33333333-3333-4333-8333-333333333332"
        private const val IMAGE_ID_3 = "33333333-3333-4333-8333-333333333333"

        private fun category() = RemoteCategoryDto(
            id = CATEGORY_ID,
            name = "Interior",
            isActive = true,
        )

        private fun product(
            id: String = PRODUCT_ID,
            priceCents: Long = 58_900L,
            images: List<RemoteProductImageDto> = emptyList(),
            createdAt: String = "2026-08-15T12:00:00Z",
            updatedAt: String = "2026-08-15T12:00:00Z",
        ) = RemoteProductDto(
            id = id,
            internalCode = "PL-001",
            barcode = "750100000001",
            commonName = "Producto remoto",
            scientificName = "Planta test",
            description = "Descripción",
            categoryId = CATEGORY_ID,
            priceCents = priceCents,
            wholesalePriceCents = null,
            unit = "pieza",
            minimumStock = 0.0,
            wateringAdvice = "Moderado",
            lightType = "Indirecta",
            recommendedClimate = "Templado",
            isActive = true,
            createdAt = createdAt,
            updatedAt = updatedAt,
            images = images,
        )

        private fun pricingFor(products: List<RemoteProductDto>) = RemoteCatalogPricingDto(
            schemaVersion = 1,
            items = products.map {
                RemoteCatalogPricingItemDto(
                    productId = it.id,
                    listPriceCents = it.priceCents,
                    effectivePriceCents = it.priceCents,
                )
            },
        )

        private fun image(id: String, sortOrder: Int, isPrimary: Boolean) = RemoteProductImageDto(
            id = id,
            productId = PRODUCT_ID,
            storagePath = if (isPrimary) "products/primary.jpg" else "products/$id.jpg",
            altText = "Imagen",
            sortOrder = sortOrder,
            isPrimary = isPrimary,
        )

        private fun session(mode: SessionMode) = UserSession(
            userId = "44444444-4444-4444-8444-444444444444",
            email = "test@example.test",
            fullName = "Test",
            role = UserRole.OWNER,
            capabilities = setOf(AppPermission.VIEW_CATALOG),
            branch = null,
            mode = mode,
        )
    }
}
