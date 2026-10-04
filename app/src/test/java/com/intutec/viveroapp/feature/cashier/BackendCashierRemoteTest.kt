package com.intutec.viveroapp.feature.cashier

import com.intutec.viveroapp.core.network.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.cashier.data.remote.BackendCashierRemoteDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackendCashierRemoteTest {
    private val who = BackendSaleIdentity(2, 3)
    private val body = """{"claim_token":"${"a".repeat(64)}","method":"CASH","amount_received_cents":600,"reference":null}"""
    private val receipt = """{"schema_version":1,"idempotent_replay":false,"sale":{"id":5,"folio":"VD-DEMO","branch_id":3,"web_order_id":null,"status":"PAID","subtotal_cents":500,"discount_cents":0,"total_cents":500},"payment":{"id":6,"sale_id":5,"branch_id":3,"cashier_id":2,"claim_id":7,"method":"CASH","amount_due_cents":500,"requested_amount_received_cents":600,"amount_received_cents":600,"change_cents":100,"reference":null,"created_at":"2026-10-02T12:00:00.000Z"}}"""
    private class Transport(var response: BackendApiResponse) : BackendApiTransport {
        var path = ""; var body = ""; var headers = emptyMap<String, String>(); var count = 0
        override suspend fun post(path: String, token: String, headers: Map<String, String>, body: String): BackendApiResponse {
            count++; this.path = path; this.body = body; this.headers = headers; return response
        }
    }
    @Test fun exactPaymentAndRecoveryPreserveIdentityAndKey() = runTest {
        val transport = Transport(BackendApiResponse(201, receipt)); val source = BackendCashierRemoteDataSource(transport)
        assertEquals(100L, source.pay("t".repeat(43), who, 5, "b".repeat(64), body).changeCents)
        assertEquals(body, transport.body); assertEquals("2", transport.headers["X-Expected-Actor-Id"]); assertEquals("3", transport.headers["X-Expected-Branch-Id"])
        source.recover("t".repeat(43), who, 5, "b".repeat(64), body)
        assertEquals("/api/v1/cashier/sales/5/payment-result", transport.path); assertEquals("{}", transport.body); assertEquals("b".repeat(64), transport.headers["Idempotency-Key"])
    }
    @Test fun wrongAccountChangedAmountAndBrokenChangeNeverConfirmPayment() = runTest {
        for (wrong in listOf(receipt.replace("\"cashier_id\":2", "\"cashier_id\":9"), receipt.replace("\"change_cents\":100", "\"change_cents\":101"), receipt.replace("\"requested_amount_received_cents\":600", "\"requested_amount_received_cents\":601"))) {
            try { BackendCashierRemoteDataSource(Transport(BackendApiResponse(201, wrong))).pay("t".repeat(43), who, 5, "b".repeat(64), body); fail("Incompatible receipt") } catch (_: IllegalStateException) { }
        }
    }
    @Test fun failedRecoveryNeverCallsPaymentEndpoint() = runTest {
        val transport = Transport(BackendApiResponse(404, "{\"error\":\"PAYMENT_NOT_FOUND\"}"))
        try { BackendCashierRemoteDataSource(transport).recover("t".repeat(43), who, 5, "b".repeat(64), body); fail("Missing receipt") } catch (_: IllegalStateException) { }
        assertEquals(1, transport.count); assertTrue(transport.path.endsWith("payment-result"))
    }
    @Test fun onlyExactMissingPaymentCanAuthorizeOriginalRetry() = runTest {
        for ((status, body) in listOf(404 to "{\"error\":\"PAYMENT_NOT_FOUND\"}", 503 to "{\"error\":\"PAYMENT_NOT_FOUND\"}", 404 to "{\"error\":\"NOT_FOUND\"}", 404 to "broken")) {
            try { BackendCashierRemoteDataSource(Transport(BackendApiResponse(status, body))).recover("t".repeat(43), who, 5, "b".repeat(64), this@BackendCashierRemoteTest.body); fail("Missing receipt") }
            catch (error: IllegalStateException) { assertEquals(status == 404 && body == "{\"error\":\"PAYMENT_NOT_FOUND\"}", error is com.intutec.viveroapp.feature.cashier.domain.repository.BackendPaymentNotFound) }
        }
    }
    @Test fun retirementRequiresExactTerminalResponseOrValidCommittedReceipt() = runTest {
        val transport = Transport(BackendApiResponse(200, "{\"schema_version\":1,\"status\":\"RETIRED\",\"receipt\":null}"))
        val source = BackendCashierRemoteDataSource(transport)
        assertEquals(com.intutec.viveroapp.feature.cashier.domain.repository.BackendPaymentRetirement.Retired, source.retire("t".repeat(43), who, 5, "b".repeat(64), body))
        assertTrue(transport.path.endsWith("payment-retire")); assertEquals("{}", transport.body)
        transport.response = BackendApiResponse(200, "{\"schema_version\":1,\"status\":\"COMMITTED\",\"receipt\":$receipt}")
        assertTrue(source.retire("t".repeat(43), who, 5, "b".repeat(64), body) is com.intutec.viveroapp.feature.cashier.domain.repository.BackendPaymentRetirement.Committed)
        transport.response = BackendApiResponse(200, "{\"schema_version\":1,\"status\":\"COMMITTED\",\"receipt\":${receipt.replace("\"PAID\"", "\"DELIVERED\"")}}")
        assertTrue(source.retire("t".repeat(43), who, 5, "b".repeat(64), body) is com.intutec.viveroapp.feature.cashier.domain.repository.BackendPaymentRetirement.Committed)
        for (wrong in listOf("{\"schema_version\":1,\"status\":\"RETIRED\",\"receipt\":{}}", "{\"schema_version\":1,\"status\":\"UNKNOWN\",\"receipt\":null}")) {
            transport.response = BackendApiResponse(200, wrong)
            try { source.retire("t".repeat(43), who, 5, "b".repeat(64), body); fail("Unsafe retirement") } catch (_: IllegalStateException) { }
        }
    }
}
