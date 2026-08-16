package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.usecase.FilterProductsUseCase
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterProductsUseCaseTest {
    private val filter = FilterProductsUseCase()
    private val interior = Category("interior", "Interior")
    private val exterior = Category("exterior", "Exterior")
    private val products = listOf(
        product("monstera", "PL-001", "75001", "Monstera", interior, stock = 8),
        product("lavanda", "PL-014", "75014", "Lavanda", exterior, stock = 0),
    )

    @Test
    fun `searches by name internal code and barcode`() {
        assertEquals("monstera", filter(products, "monster", null, false).single().id)
        assertEquals("lavanda", filter(products, "PL-014", null, false).single().id)
        assertEquals("lavanda", filter(products, "75014", null, false).single().id)
    }

    @Test
    fun `filters category`() {
        val result = filter(products, "", exterior.id, false)
        assertEquals(listOf("lavanda"), result.map(Product::id))
    }

    @Test
    fun `available filter excludes zero stock`() {
        val result = filter(products, "", null, true)
        assertEquals(listOf("monstera"), result.map(Product::id))
        assertTrue(result.all(Product::isAvailable))
    }

    @Test
    fun `available filter keeps active products while stock is unknown`() {
        val unknown = product("remote", "PL-099", "75999", "Remoto", interior, stock = 0)
            .copy(stockKnown = false)

        val result = filter(listOf(unknown), "", null, true)

        assertEquals(listOf("remote"), result.map(Product::id))
    }

    private fun product(id: String, code: String, barcode: String, name: String, category: Category, stock: Int) = Product(
        id = id,
        internalCode = code,
        barcode = barcode,
        commonName = name,
        scientificName = null,
        description = "Demo",
        category = category,
        priceCents = 10000,
        wholesalePriceCents = null,
        unit = "pieza",
        stockAvailable = stock,
        minimumStock = 1,
        imageKey = "monstera",
        wateringAdvice = "Moderado",
        lightType = "Indirecta",
        recommendedClimate = "Templado",
        isActive = true,
        promotion = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
