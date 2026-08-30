package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.feature.cart.data.repository.nextCartItem
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoomCartMergeTest {
    @Test
    fun `adding an existing product increments the same cart row`() {
        val first = nextCartItem(null, product())
        val second = nextCartItem(first, product())

        assertEquals(first.productId, second.productId)
        assertEquals(2, second.quantity)
    }

    @Test
    fun `intentional increment cannot exceed current known stock`() {
        val onlyUnit = product(stock = 1)
        val first = nextCartItem(null, onlyUnit)

        assertThrows(IllegalArgumentException::class.java) {
            nextCartItem(first, onlyUnit)
        }
    }

    private fun product(stock: Int = 5) = Product(
        id = "11111111-1111-4111-8111-111111111111",
        internalCode = "PL-ALOE-001",
        barcode = null,
        commonName = "Aloe vera",
        scientificName = null,
        description = "",
        category = Category("suculentas", "Suculentas"),
        priceCents = 12_500,
        wholesalePriceCents = null,
        unit = "pieza",
        stockAvailable = stock,
        minimumStock = 1,
        imageKey = "",
        wateringAdvice = "",
        lightType = "",
        recommendedClimate = "",
        isActive = true,
        promotion = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
