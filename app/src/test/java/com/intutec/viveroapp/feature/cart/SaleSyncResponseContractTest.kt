package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.feature.cart.sync.decodeSaleSyncResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class SaleSyncResponseContractTest {
    @Test
    fun `decodes exact scalar composite response from submit sale RPC`() {
        val response = decodeSaleSyncResponse(RPC_RESPONSE)

        assertEquals(SALE_ID, response.id)
        assertEquals(FOLIO, response.folio)
        assertEquals(USER_ID, response.createdBy)
        assertEquals(BRANCH_ID, response.branchId)
        assertEquals("SENT_TO_CASHIER", response.status)
    }

    @Test
    fun `accepts a single record array without changing confirmation fields`() {
        val response = decodeSaleSyncResponse("[$RPC_RESPONSE]")

        assertEquals(SALE_ID, response.id)
        assertEquals(FOLIO, response.folio)
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects multiple records instead of confirming an ambiguous sale`() {
        decodeSaleSyncResponse("[$RPC_RESPONSE,$RPC_RESPONSE]")
    }

    private companion object {
        const val SALE_ID = "d364c8f0-2be6-4604-acdc-6392cfa5db75"
        const val FOLIO = "VD-260815-D364C8"
        const val USER_ID = "dd99d229-3678-4616-8703-d9f2a41f69d6"
        const val BRANCH_ID = "d396d63f-0360-47e3-a529-928f689c6297"
        const val RPC_RESPONSE = """{"id":"$SALE_ID","folio":"$FOLIO","branch_id":"$BRANCH_ID","customer_id":null,"subtotal_cents":10000,"discount_cents":0,"total_cents":10000,"status":"SENT_TO_CASHIER","created_by":"$USER_ID","idempotency_key":"$SALE_ID","created_at":"2026-08-16T05:16:47.912301+00:00","updated_at":"2026-08-16T05:16:47.912301+00:00"}"""
    }
}
