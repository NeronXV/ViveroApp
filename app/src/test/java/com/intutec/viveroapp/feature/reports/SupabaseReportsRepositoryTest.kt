package com.intutec.viveroapp.feature.reports

import com.intutec.viveroapp.feature.reports.data.remote.ReportsRemoteDataSource
import com.intutec.viveroapp.feature.reports.data.repository.SupabaseReportsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class SupabaseReportsRepositoryTest {

    @Test
    fun `parses daily sales JSON correctly`() = runTest {
        val branchId = UUID.randomUUID().toString()
        val json = """
            [
                {
                    "branchId": "$branchId",
                    "branchName": "Centro",
                    "day": "2026-08-29",
                    "salesCount": 10,
                    "revenueCents": 500000,
                    "discountCents": 5000
                }
            ]
        """.trimIndent()

        val remote = FakeReportsRemoteDataSource(dailyJson = json)
        val repository = SupabaseReportsRepository(remote)

        val result = repository.getDailySales()
        
        assertTrue(result.isSuccess)
        val daily = result.getOrThrow().single()
        assertEquals(branchId, daily.branchId)
        assertEquals("Centro", daily.branchName)
        assertEquals(LocalDate.of(2026, 8, 29), daily.day)
        assertEquals(10, daily.salesCount)
        assertEquals(500000L, daily.revenueCents)
        assertEquals(5000L, daily.discountCents)
    }

    @Test
    fun `parses top products JSON correctly`() = runTest {
        val productId = UUID.randomUUID().toString()
        val json = """
            [
                {
                    "productId": "$productId",
                    "productName": "Monstera",
                    "productCode": "PL-001",
                    "totalQuantity": 15.5,
                    "totalRevenueCents": 150000
                }
            ]
        """.trimIndent()

        val remote = FakeReportsRemoteDataSource(topJson = json)
        val repository = SupabaseReportsRepository(remote)

        val result = repository.getTopProducts()

        assertTrue(result.isSuccess)
        val product = result.getOrThrow().single()
        assertEquals(productId, product.productId)
        assertEquals("Monstera", product.productName)
        assertEquals("PL-001", product.productCode)
        assertEquals(15.5, product.totalQuantity, 0.001)
        assertEquals(150000L, product.totalRevenueCents)
    }

    @Test
    fun `rejects invalid UUID in reports`() = runTest {
        val json = """
            [
                {
                    "branchId": "not-a-uuid",
                    "branchName": "Centro",
                    "day": "2026-08-29",
                    "salesCount": 1,
                    "revenueCents": 100,
                    "discountCents": 0
                }
            ]
        """.trimIndent()

        val remote = FakeReportsRemoteDataSource(dailyJson = json)
        val repository = SupabaseReportsRepository(remote)

        val result = repository.getDailySales()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("ID de sucursal inválido") == true)
    }

    private class FakeReportsRemoteDataSource(
        val dailyJson: String = "[]",
        val topJson: String = "[]"
    ) : ReportsRemoteDataSource {
        override suspend fun getDailySales(branchId: String?, startDate: LocalDate?, endDate: LocalDate?): String = dailyJson
        override suspend fun getTopProducts(branchId: String?, limit: Int): String = topJson
    }
}
