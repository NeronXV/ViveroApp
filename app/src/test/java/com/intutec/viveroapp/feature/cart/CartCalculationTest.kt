package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CartCalculationTest {
    @Test
    fun `keeps several products and calculates totals using cents`() {
        val cart = Cart(
            items = listOf(
                item("monstera", listPrice = 58_900, unitPrice = 52_900, quantity = 2, stock = 5),
                item("lavanda", listPrice = 18_900, unitPrice = 18_900, quantity = 1, stock = 4),
            ),
        )

        assertEquals(136_700, cart.subtotalCents)
        assertEquals(12_000, cart.discountCents)
        assertEquals(124_700, cart.totalCents)
        assertEquals(3, cart.itemCount)
        assertEquals(listOf("monstera", "lavanda"), cart.items.map(CartItem::productId))
    }

    @Test
    fun `rejects zero quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            item("monstera", 10_000, 10_000, quantity = 0, stock = 4)
        }
    }

    @Test
    fun `rejects quantity greater than stock`() {
        assertThrows(IllegalArgumentException::class.java) {
            item("monstera", 10_000, 10_000, quantity = 5, stock = 4)
        }
    }

    @Test
    fun `rejects negative price`() {
        assertThrows(IllegalArgumentException::class.java) {
            item("monstera", -1, 0, quantity = 1, stock = 4)
        }
    }

    private fun item(id: String, listPrice: Long, unitPrice: Long, quantity: Int, stock: Int) = CartItem(
        productId = id,
        internalCode = "PL-001",
        name = "Producto",
        imageKey = "monstera",
        unit = "pieza",
        listPriceCents = listPrice,
        unitPriceCents = unitPrice,
        quantity = quantity,
        stockAvailable = stock,
    )
}
