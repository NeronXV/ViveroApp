package com.intutec.viveroapp.feature.cashier

import com.intutec.viveroapp.feature.cashier.data.remote.CashierRemoteDataSource
import com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierOrderDto
import com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierOrderItemDto
import com.intutec.viveroapp.feature.cashier.data.repository.SupabaseCashierRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierRepositoryTest {
    @Test
    fun `maps real UUIDs cents items and offset timestamps`() = runTest {
        val repository = SupabaseCashierRepository(StubRemote(orders = listOf(order())))

        val detail = repository.getPendingOrder(BRANCH_ID, SALE_ID).getOrThrow()

        assertEquals(SALE_ID, detail.summary.id)
        assertEquals(BRANCH_ID, detail.summary.branchId)
        assertEquals(10_000L, detail.summary.totalCents)
        assertEquals(1, detail.summary.productCount)
        assertEquals(1, detail.summary.unitCount)
        assertEquals("2026-08-16T06:48:21.128443Z", detail.summary.createdAt.toString())
        assertEquals(PRODUCT_ID, detail.items.single().productId)
    }

    @Test
    fun `orders queue by oldest created at`() = runTest {
        val newer = order(id = SALE_ID_2, folio = "VD-260815-BBBBBB", createdAt = "2026-08-16T01:00:00Z")
        val older = order(createdAt = "2026-08-15T23:00:00Z")
        val repository = SupabaseCashierRepository(StubRemote(orders = listOf(newer, older)))

        val result = repository.getPendingOrders(BRANCH_ID).getOrThrow()

        assertEquals(listOf(SALE_ID, SALE_ID_2), result.map { it.id })
    }

    @Test
    fun `accepts authoritative product promotion as effective line subtotal`() = runTest {
        val promotedItem = item().copy(
            listPriceCents = 10_000,
            unitPriceCents = 7_500,
            discountCents = 2_500,
            lineTotalCents = 7_500,
        )
        val promotedOrder = order().copy(
            subtotalCents = 7_500,
            totalCents = 7_500,
            items = listOf(promotedItem),
        )
        val repository = SupabaseCashierRepository(StubRemote(orders = listOf(promotedOrder)))

        val detail = repository.getPendingOrder(BRANCH_ID, SALE_ID).getOrThrow()

        assertEquals(7_500L, detail.subtotalCents)
        assertEquals(2_500L, detail.items.single().discountCents)
        assertEquals(7_500L, detail.summary.totalCents)
    }

    @Test
    fun `rejects row returned from another branch`() = runTest {
        val repository = SupabaseCashierRepository(
            StubRemote(orders = listOf(order().copy(branchId = OTHER_BRANCH_ID))),
        )

        assertTrue(repository.getPendingOrder(BRANCH_ID, SALE_ID).isFailure)
    }

    @Test
    fun `rejects non cashier status and inconsistent totals`() = runTest {
        val wrongStatus = SupabaseCashierRepository(
            StubRemote(orders = listOf(order().copy(status = "PAID"))),
        )
        val wrongTotal = SupabaseCashierRepository(
            StubRemote(orders = listOf(order().copy(totalCents = 9_999))),
        )

        assertTrue(wrongStatus.getPendingOrder(BRANCH_ID, SALE_ID).isFailure)
        assertTrue(wrongTotal.getPendingOrder(BRANCH_ID, SALE_ID).isFailure)
    }

    @Test
    fun `rejects item attached to another sale`() = runTest {
        val invalid = order().copy(items = listOf(item().copy(saleId = SALE_ID_2)))
        val repository = SupabaseCashierRepository(StubRemote(orders = listOf(invalid)))

        assertTrue(repository.getPendingOrder(BRANCH_ID, SALE_ID).isFailure)
    }

    private class StubRemote(
        private val orders: List<RemoteCashierOrderDto>,
    ) : CashierRemoteDataSource {
        override suspend fun loadPendingOrders(branchId: String) = orders
        override suspend fun loadPendingOrder(branchId: String, orderId: String) = orders
    }

    companion object {
        const val SALE_ID = "58a8bd9a-1530-4d16-bc38-ef8ae6348735"
        const val SALE_ID_2 = "d364c8f0-2be6-4604-acdc-6392cfa5db75"
        const val BRANCH_ID = "d396d63f-0360-47e3-a529-928f689c6297"
        const val OTHER_BRANCH_ID = "11111111-1111-4111-8111-111111111111"
        const val USER_ID = "dd99d229-3678-4616-8703-d9f2a41f69d6"
        const val PRODUCT_ID = "ea963006-cdc0-4a98-acb9-098adb106079"
        const val ITEM_ID = "6dc15270-b382-4be2-aa90-05147670886a"

        fun order(
            id: String = SALE_ID,
            folio: String = "VD-260815-58A8BD",
            createdAt: String = "2026-08-15T23:48:21.128443-07:00",
        ) = RemoteCashierOrderDto(
            id = id,
            folio = folio,
            branchId = BRANCH_ID,
            createdBy = USER_ID,
            subtotalCents = 10_000,
            discountCents = 0,
            totalCents = 10_000,
            status = "SENT_TO_CASHIER",
            createdAt = createdAt,
            updatedAt = createdAt,
            items = listOf(item(saleId = id)),
        )

        fun item(saleId: String = SALE_ID) = RemoteCashierOrderItemDto(
            id = ITEM_ID,
            saleId = saleId,
            productId = PRODUCT_ID,
            productName = "Planta de prueba",
            internalCode = "STG-PLANTA-001",
            quantity = 1,
            listPriceCents = 10_000,
            unitPriceCents = 10_000,
            discountCents = 0,
            lineTotalCents = 10_000,
        )
    }
}
