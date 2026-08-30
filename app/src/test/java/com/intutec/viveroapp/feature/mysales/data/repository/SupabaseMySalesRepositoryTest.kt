package com.intutec.viveroapp.feature.mysales.data.repository

import com.intutec.viveroapp.feature.mysales.data.remote.MySalesRemoteDataSource
import com.intutec.viveroapp.feature.mysales.domain.model.MySaleStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SupabaseMySalesRepositoryTest {

    @Test
    fun `parses valid my sales JSON correctly`() = runTest {
        val saleId = UUID.randomUUID().toString()
        val createdAt = "2026-08-29T10:00:00Z"
        val updatedAt = "2026-08-29T11:00:00Z"
        val serverTime = "2026-08-29T12:00:00Z"
        val json = """
            {
                "schemaVersion": 1,
                "items": [
                    {
                        "id": "$saleId",
                        "folio": "VD-260829-000001",
                        "status": "SENT_TO_CASHIER",
                        "createdAt": "$createdAt",
                        "updatedAt": "$updatedAt",
                        "subtotalCents": 10000,
                        "discountCents": 500,
                        "totalCents": 9500,
                        "itemCount": 2,
                        "totalQuantity": 3,
                        "paidAt": null
                    }
                ],
                "page": {
                    "limit": 20,
                    "hasMore": false,
                    "nextCursor": null
                },
                "serverTime": "$serverTime"
            }
        """.trimIndent()

        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        val sale = page.items.single()
        assertEquals(saleId, sale.id)
        assertEquals(MySaleStatus.SENT_TO_CASHIER, sale.status)
        assertEquals(9500L, sale.totalCents)
    }

    @Test
    fun `rejects incompatible schemaVersion`() = runTest {
        val json = """{"schemaVersion": 2, "items": [], "page": {"limit": 20, "hasMore": false}, "serverTime": "${Instant.now()}"}"""
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("compatible") == true)
    }

    @Test
    fun `rejects remote page limit different from requested limit`() = runTest {
        val json = validResponseJson().replace("\"limit\": 20", "\"limit\": 19")
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))

        val result = repository.getMyRecentSales(limit = 20)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("límite", ignoreCase = true) == true)
    }

    @Test
    fun `rejects extra unknown keys`() = runTest {
        val json = """{"schemaVersion": 1, "extra": "forbidden", "items": [], "page": {"limit": 20, "hasMore": false}, "serverTime": "${Instant.now()}"}"""
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects inconsistent total`() = runTest {
        val json = validResponseJson(total = 800, subtotal = 1000, discount = 100)
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("inconsistentes") == true)
    }

    @Test
    fun `rejects negative amounts`() = runTest {
        val json = validResponseJson(subtotal = -100)
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects discount greater than subtotal`() = runTest {
        val json = validResponseJson(subtotal = 1000, discount = 1100, total = -100)
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects PAID without paidAt`() = runTest {
        val json = validResponseJson(status = "PAID", paidAt = null)
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("fecha de pago") == true)
    }

    @Test
    fun `rejects DELIVERED without paidAt`() = runTest {
        val json = validResponseJson(status = "DELIVERED", paidAt = null)
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("fecha de pago") == true)
    }

    @Test
    fun `rejects unpaid status with paidAt`() = runTest {
        val json = validResponseJson(
            status = "SENT_TO_CASHIER",
            paidAt = "2026-08-29T10:00:00Z",
        )
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))

        val result = repository.getMyRecentSales()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("fecha de pago") == true)
    }

    @Test
    fun `rejects unknown status`() = runTest {
        val json = validResponseJson(status = "PROCESING")
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Estado de venta desconocido") == true)
    }

    @Test
    fun `rejects zero itemCount or quantity`() = runTest {
        val json = validResponseJson(itemCount = 0)
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        assertTrue(repository.getMyRecentSales().isFailure)
    }

    @Test
    fun `rejects updatedAt before createdAt`() = runTest {
        val json = validResponseJson(created = "2026-08-29T12:00:00Z", updated = "2026-08-29T11:00:00Z")
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("anterior") == true)
    }

    @Test
    fun `rejects duplicate IDs in list`() = runTest {
        val id = UUID.randomUUID().toString()
        val json = """
            {
                "schemaVersion": 1,
                "items": [
                    ${itemJson(id = id, folio = "VD-260829-000001")},
                    ${itemJson(id = id, folio = "VD-260829-000002")}
                ],
                "page": {"limit": 20, "hasMore": false},
                "serverTime": "${Instant.now()}"
            }
        """.trimIndent()
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("duplicados") == true)
    }

    @Test
    fun `rejects sales outside stable descending order`() = runTest {
        val olderId = "11111111-1111-4111-8111-111111111111"
        val newerId = "22222222-2222-4222-8222-222222222222"
        val json = """
            {
                "schemaVersion": 1,
                "items": [
                    ${itemJson(id = olderId, folio = "VD-260829-000001", created = "2026-08-29T10:00:00Z")},
                    ${itemJson(id = newerId, folio = "VD-260829-000002", created = "2026-08-29T11:00:00Z")}
                ],
                "page": {"limit": 20, "hasMore": false},
                "serverTime": "2026-08-29T12:00:00Z"
            }
        """.trimIndent()
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))

        val result = repository.getMyRecentSales()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("orden estable") == true)
    }

    @Test
    fun `rejects sale timestamp after server time`() = runTest {
        val json = validResponseJson(
            created = "2099-08-29T10:00:00Z",
            updated = "2099-08-29T10:00:00Z",
        )
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))

        val result = repository.getMyRecentSales()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("hora del servidor") == true)
    }

    @Test
    fun `sends exact limit and complete cursor to remote data source`() = runTest {
        val cursorId = UUID.randomUUID().toString()
        val cursorTime = Instant.parse("2026-08-29T10:00:00Z")
        val remote = FakeMySalesRemoteDataSource(
            """{"schemaVersion":1,"items":[],"page":{"limit":7,"hasMore":false},"serverTime":"2026-08-29T12:00:00Z"}"""
        )
        val repository = SupabaseMySalesRepository(remote)

        val result = repository.getMyRecentSales(7, cursorTime, cursorId)

        assertTrue(result.isSuccess)
        assertEquals(7, remote.receivedLimit)
        assertEquals(cursorTime, remote.receivedAfterCreatedAt)
        assertEquals(cursorId, remote.receivedAfterId)
    }

    @Test
    fun `rethrows CancellationException`() = runTest {
        val remote = object : MySalesRemoteDataSource {
            override suspend fun getMyRecentSales(limit: Int, afterCreatedAt: Instant?, afterId: String?): String {
                throw CancellationException("Cancelled")
            }
        }
        val repository = SupabaseMySalesRepository(remote)
        try {
            // Using runCatching will capture the CancellationException, 
            // but the repository rethrows it before runCatching can finish its block.
            // Wait, runCatching is inside the repository's getMyRecentSales.
            // So getMyRecentSales will rethrow.
            repository.getMyRecentSales()
            fail("Should have thrown CancellationException")
        } catch (e: CancellationException) {
            // Success
        }
    }

    @Test
    fun `rejects inconsistent remote cursor`() = runTest {
        val id1 = UUID.randomUUID().toString()
        val t1 = "2026-08-29T10:00:00Z"
        val json = """
            {
                "schemaVersion": 1,
                "items": [${itemJson(id = id1, created = t1)}],
                "page": {
                    "limit": 20,
                    "hasMore": true,
                    "nextCursor": {"id": "${UUID.randomUUID()}", "createdAt": "$t1"}
                },
                "serverTime": "${Instant.now()}"
            }
        """.trimIndent()
        val repository = SupabaseMySalesRepository(FakeMySalesRemoteDataSource(json))
        val result = repository.getMyRecentSales()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("incoherente") == true)
    }

    private fun validResponseJson(
        status: String = "SENT_TO_CASHIER",
        total: Long = 1000,
        subtotal: Long = 1000,
        discount: Long = 0,
        paidAt: String? = null,
        itemCount: Int = 1,
        quantity: Int = 1,
        created: String = "2026-08-29T10:00:00Z",
        updated: String = "2026-08-29T10:00:00Z"
    ) = """
        {
            "schemaVersion": 1,
            "items": [
                {
                    "id": "${UUID.randomUUID()}",
                    "folio": "VD-260829-000001",
                    "status": "$status",
                    "createdAt": "$created",
                    "updatedAt": "$updated",
                    "subtotalCents": $subtotal,
                    "discountCents": $discount,
                    "totalCents": $total,
                    "itemCount": $itemCount,
                    "totalQuantity": $quantity,
                    "paidAt": ${if (paidAt == null) "null" else "\"$paidAt\""}
                }
            ],
            "page": {"limit": 20, "hasMore": false},
            "serverTime": "${Instant.now()}"
        }
    """.trimIndent()

    private fun itemJson(
        id: String = UUID.randomUUID().toString(),
        folio: String = "VD-260829-000001",
        created: String = "2026-08-29T10:00:00Z"
    ) = """
        {
            "id": "$id",
            "folio": "$folio",
            "status": "SENT_TO_CASHIER",
            "createdAt": "$created",
            "updatedAt": "$created",
            "subtotalCents": 1000,
            "discountCents": 0,
            "totalCents": 1000,
            "itemCount": 1,
            "totalQuantity": 1,
            "paidAt": null
        }
    """.trimIndent()

    private class FakeMySalesRemoteDataSource(private val response: String) : MySalesRemoteDataSource {
        var receivedLimit: Int? = null
        var receivedAfterCreatedAt: Instant? = null
        var receivedAfterId: String? = null

        override suspend fun getMyRecentSales(limit: Int, afterCreatedAt: Instant?, afterId: String?): String {
            receivedLimit = limit
            receivedAfterCreatedAt = afterCreatedAt
            receivedAfterId = afterId
            return response
        }
    }
}
