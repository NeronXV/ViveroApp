package com.intutec.viveroapp.feature.cart.data.remote

import com.intutec.viveroapp.core.network.BackendApiResponse
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.core.network.backendApiOrigin
import com.intutec.viveroapp.feature.cart.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BackendSaleRemoteDataSourceTest {
    private val identity = BackendSaleIdentity(2, 3)
    private val lines = listOf(BackendSaleLine(4, 2))
    private val token = "a".repeat(43)
    private fun attempt() = BackendSaleAttempt.restore(identity, "b".repeat(64), lines, 900)
    private fun receipt() = """{"schema_version":1,"id":9,"folio":"VD-AAAAAAAAAAAAAAAAAAAAAAAA","branch_id":3,"created_by":2,"status":"SENT_TO_CASHIER","subtotal_cents":900,"discount_cents":0,"total_cents":900,"created_at":"2026-10-01T12:00:00Z","idempotent_replay":false}"""
    private class Fake(var response: BackendApiResponse) : BackendApiTransport {
        var count = 0; var path = ""; var headers = emptyMap<String, String>(); var body = ""
        var error: Exception? = null
        override suspend fun post(path: String, token: String, headers: Map<String, String>, body: String): BackendApiResponse {
            count++; this.path = path; this.headers = headers; this.body = body
            error?.let { throw it }; return response
        }
    }
    @Test fun exactSubmissionAndRecoveryPreserveIdentityKeyAndPayload() = runTest {
        val fake = Fake(BackendApiResponse(201, receipt())); val source = BackendSaleRemoteDataSource(fake); val saved = attempt()
        assertEquals(9L, source.submit(token, saved).id)
        assertEquals("/api/v1/sales", fake.path)
        val body = Json.parseToJsonElement(fake.body).jsonObject
        assertEquals(setOf("items", "expected_total_cents"), body.keys)
        assertEquals("2", fake.headers["X-Expected-Actor-Id"])
        assertEquals("3", fake.headers["X-Expected-Branch-Id"])
        assertEquals(saved.key, fake.headers["Idempotency-Key"])
        source.recover(token, saved)
        assertEquals("/api/v1/sales/recover", fake.path); assertEquals("{}", fake.body)
        assertEquals(saved.key, fake.headers["Idempotency-Key"])
    }
    @Test fun rejectsWrongIdentityUnsafeMoneyAndUnknownFieldsAfterWriteAsUncertain() = runTest {
        for (body in listOf(receipt().replace("\"created_by\":2", "\"created_by\":8"), receipt().replace("\"total_cents\":900", "\"total_cents\":901"), receipt().replace("\"id\":9", "\"id\":\"9\""), receipt().dropLast(1) + ",\"extra\":1}")) {
            val source = BackendSaleRemoteDataSource(Fake(BackendApiResponse(201, body)))
            try { source.submit(token, attempt()); fail("Receipt accepted") } catch (error: BackendSaleException) { assertTrue(error.resultUncertain) }
        }
    }
    @Test fun noAutomaticRetryAndCancellationIsPropagated() = runTest {
        val fake = Fake(BackendApiResponse(201, receipt())); fake.error = IllegalStateException("offline")
        try { BackendSaleRemoteDataSource(fake).submit(token, attempt()); fail("Expected failure") } catch (error: BackendSaleException) { assertTrue(error.resultUncertain) }
        assertEquals(1, fake.count)
        fake.error = CancellationException("cancelled")
        try { BackendSaleRemoteDataSource(fake).submit(token, attempt()); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
    @Test fun priceConflictIsDefinitiveAndRecoveryDoesNotWrite() = runTest {
        val fake = Fake(BackendApiResponse(409, """{"error":"SALE_PRICE_CHANGED"}"""))
        try { BackendSaleRemoteDataSource(fake).submit(token, attempt()); fail("Expected conflict") } catch (error: BackendSaleException) { assertFalse(error.resultUncertain); assertEquals("SALE_PRICE_CHANGED", error.code) }
    }
    @Test fun inputSnapshotRejectsUuidKeysAndCopiesMutableItems() {
        val mutable = lines.toMutableList(); val saved = BackendSaleAttempt.create(identity, mutable, 900)
        mutable.clear(); assertEquals(lines, saved.items); assertTrue(Regex("^[a-f0-9]{64}$").matches(saved.key))
        assertThrows(IllegalArgumentException::class.java) { BackendSaleAttempt.restore(identity, "uuid", lines, 900) }
        assertThrows(IllegalArgumentException::class.java) { BackendSaleAttempt.create(identity, lines + lines, 900) }
        assertThrows(IllegalArgumentException::class.java) { BackendSaleLine(0, 1) }
    }
    @Test fun backendOriginAllowsOnlyExplicitHttpsOrLocalDebug() {
        assertEquals("https://api.example.invalid", backendApiOrigin("https://api.example.invalid/", false))
        assertEquals("http://10.0.2.2:3001", backendApiOrigin("http://10.0.2.2:3001", true))
        for (value in listOf("http://10.0.2.2:3001", "https://user:secret@example.invalid", "https://example.invalid/api", "https://example.invalid?token=x", "")) {
            assertThrows(IllegalArgumentException::class.java) { backendApiOrigin(value, false) }
        }
    }
    @Test fun quoteChecksExactProductsAndServerTotals() = runTest {
        val body = """{"schema_version":1,"branch_id":3,"subtotal_cents":1000,"discount_cents":100,"total_cents":900,"items":[{"product_id":4,"product_name":"Demo","internal_code":"DEMO","quantity":2,"list_price_cents":500,"unit_price_cents":450,"promotion_id":1,"promotion_name":"Demo descuento","line_total_cents":900}]}"""
        val fake = Fake(BackendApiResponse(200, body)); val source = BackendSaleRemoteDataSource(fake)
        assertEquals(900L, source.quote(token, identity, lines).totalCents)
        assertEquals("/api/v1/sales/quote", fake.path)
        fake.response = BackendApiResponse(200, body.replace("\"quantity\":2", "\"quantity\":1"))
        try { source.quote(token, identity, lines); fail("Wrong quantities") } catch (error: BackendSaleException) { assertFalse(error.resultUncertain) }
    }
    @Test fun retirementUsesOriginalKeyIdentityAndEmptyBody() = runTest {
        val fake = Fake(BackendApiResponse(200, """{"schema_version":1,"status":"RETIRED","sale":null}"""))
        val saved = attempt()
        assertEquals(BackendSaleRetirement.Retired, BackendSaleRemoteDataSource(fake).retire(token, saved))
        assertEquals("/api/v1/sales/retire", fake.path); assertEquals("{}", fake.body)
        assertEquals(saved.key, fake.headers["Idempotency-Key"])
        assertEquals("2", fake.headers["X-Expected-Actor-Id"]); assertEquals("3", fake.headers["X-Expected-Branch-Id"])
        assertEquals(1, fake.count)
    }
    @Test fun committedRetirementValidatesAndReturnsActualReceipt() = runTest {
        val fake = Fake(BackendApiResponse(200, """{"schema_version":1,"status":"COMMITTED","sale":${receipt()}}"""))
        val result = BackendSaleRemoteDataSource(fake).retire(token, attempt()) as BackendSaleRetirement.Committed
        assertEquals(9L, result.receipt.id); assertEquals(identity, result.receipt.identity)
    }
    @Test fun incompatibleRetirementNeverConfirmsClosure() = runTest {
        for (body in listOf(
            """{"schema_version":1,"status":"RETIRED","sale":{}}""",
            """{"schema_version":1,"status":"RETIRED"}""",
            """{"schema_version":1,"status":"UNKNOWN","sale":null}""",
            """{"schema_version":2,"status":"RETIRED","sale":null}""",
            """{"schema_version":1,"status":"COMMITTED","sale":${receipt().replace("\"created_by\":2", "\"created_by\":8")}}""",
        )) {
            try { BackendSaleRemoteDataSource(Fake(BackendApiResponse(200, body))).retire(token, attempt()); fail("Accepted malformed closure") }
            catch (error: BackendSaleException) { assertTrue(error.resultUncertain) }
        }
    }
    @Test fun missingRecoveryOrFailedRetirementNeverCountsAsRetired() = runTest {
        for (response in listOf(BackendApiResponse(404, """{"error":"SALE_NOT_FOUND"}"""), BackendApiResponse(503, """{"error":"UNAVAILABLE"}"""))) {
            try { BackendSaleRemoteDataSource(Fake(response)).retire(token, attempt()); fail("Accepted error as closure") }
            catch (error: BackendSaleException) { assertEquals(response.status, error.status) }
        }
        val fake = Fake(BackendApiResponse(200, "{}")); fake.error = IllegalStateException("offline")
        try { BackendSaleRemoteDataSource(fake).retire(token, attempt()); fail("Accepted transport failure") }
        catch (error: BackendSaleException) { assertTrue(error.resultUncertain) }
        assertEquals(1, fake.count)
    }
}
