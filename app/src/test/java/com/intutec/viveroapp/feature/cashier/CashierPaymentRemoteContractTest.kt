package com.intutec.viveroapp.feature.cashier

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierPaymentRemoteContractTest {
    @Test fun `payment integration uses only three RPC and exact parameter names`() {
        val source = File("src/main/java/com/intutec/viveroapp/feature/cashier/data/remote/SupabaseCashierPaymentRemoteDataSource.kt").readText()
        listOf("claim_sale_for_payment", "release_sale_payment_claim", "confirm_sale_payment").forEach {
            assertTrue(source.contains("\"$it\""))
        }
        listOf("p_sale_id", "p_claim_token", "p_idempotency_key", "p_method", "p_amount_received_cents", "p_reference").forEach {
            assertTrue(source.contains("\"$it\""))
        }
        listOf(".insert(", ".update(", ".delete(", ".upsert(", "service_role").forEach {
            assertFalse(source.contains(it))
        }
    }

    @Test fun `payment DTO uses bigint compatible Long and server timestamps as ISO strings`() {
        val dto = File("src/main/java/com/intutec/viveroapp/feature/cashier/data/remote/CashierPaymentDtos.kt").readText()
        assertTrue(dto.contains("amountDueCents: Long"))
        assertTrue(dto.contains("amountReceivedCents: Long"))
        assertTrue(dto.contains("changeCents: Long"))
        assertTrue(dto.contains("expiresAt: String"))
        assertFalse(dto.contains("Double"))
    }
}
