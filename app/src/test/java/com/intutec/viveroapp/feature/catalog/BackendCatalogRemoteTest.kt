package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.core.network.BackendApiResponse
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.feature.catalog.data.remote.BackendCatalogException
import com.intutec.viveroapp.feature.catalog.data.remote.BackendCatalogRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackendCatalogRemoteTest {
    private val token = "a".repeat(43)
    private val product = """{"id":2,"internal_code":"DEMO","barcode":null,"common_name":"Planta demo","scientific_name":null,"description":"","category_id":3,"price_cents":1000,"effective_price_cents":900,"unit":"maceta","is_active":true,"watering_advice":"","light_type":"","recommended_climate":"","image":{"id":5,"url":"/api/v1/images/5","alt_text":"Demo"},"active_promotion":{"id":6,"name":"Demo","discount_percent":10}}"""
    private class Fake(var body: String) : BackendApiTransport {
        var query = emptyMap<String, String>(); var path = ""; var sent = ""; var calls = 0
        var status = 200; var error: Exception? = null
        private fun response(): BackendApiResponse { calls++; error?.let { throw it }; return BackendApiResponse(status, body) }
        override suspend fun get(path: String, token: String, query: Map<String, String>): BackendApiResponse { this.path = path; this.query = query; return response() }
        override suspend fun post(path: String, token: String, headers: Map<String, String>, body: String): BackendApiResponse { this.path = path; sent = body; return response() }
    }
    @Test fun pagePreservesIntegerPricesAndServerPromotion() = runTest {
        val fake = Fake("""{"items":[$product],"next_after_id":2}""")
        val page = BackendCatalogRemoteDataSource(fake).products(token, 1, 1, "  planta & cactus  ", 3)
        assertEquals(2L, page.nextAfterId); assertEquals(900L, page.items.single().effectivePriceCents)
        assertEquals("/api/v1/images/5", page.items.single().image?.path)
        assertEquals(mapOf("limit" to "1", "after_id" to "1", "search" to "planta & cactus", "category_id" to "3"), fake.query)
        assertEquals("/api/v1/products", fake.path)
    }
    @Test fun categoriesSupportEmptyPageAndCursor() = runTest {
        val fake = Fake("""{"items":[{"id":3,"name":"Demo","description":"","is_active":true}],"next_after_id":null}""")
        assertEquals(3L, BackendCatalogRemoteDataSource(fake).categories(token).items.single().id)
        fake.body = """{"items":[],"next_after_id":null}"""
        assertTrue(BackendCatalogRemoteDataSource(fake).categories(token).items.isEmpty())
    }
    @Test fun scanReturnsProductOrNullAndTrimsCode() = runTest {
        val fake = Fake("""{"schema_version":1,"item":$product}""")
        assertEquals(2L, BackendCatalogRemoteDataSource(fake).scan(token, " DEMO ")?.id)
        assertEquals("/api/v1/products/scan", fake.path); assertEquals("""{"code":"DEMO"}""", fake.sent)
        fake.body = """{"schema_version":1,"item":null}"""
        assertNull(BackendCatalogRemoteDataSource(fake).scan(token, "none"))
    }
    @Test fun rejectsInvalidContractPricesImagesAndInactiveProducts() = runTest {
        for (bad in listOf(product.replace("\"id\":2", "\"id\":\"2\""), product.replace("\"effective_price_cents\":900", "\"effective_price_cents\":1001"),
            product.replace("/api/v1/images/5", "https://evil.invalid/image"), product.replace("\"is_active\":true", "\"is_active\":false"),
            product.replace("\"price_cents\":1000", "\"price_cents\":9007199254740992"), product.replace("\"discount_percent\":10", "\"discount_percent\":\"10\""))) {
            try { BackendCatalogRemoteDataSource(Fake("""{"items":[$bad],"next_after_id":null}""")).products(token); fail("Accepted invalid contract") }
            catch (error: BackendCatalogException) { assertEquals(502, error.status) }
        }
    }
    @Test fun rejectsBrokenPaginationAndCategoryMismatch() = runTest {
        for (body in listOf("""{"items":[$product,$product],"next_after_id":null}""", """{"items":[$product],"next_after_id":9}""", """{"items":[],"next_after_id":2}""")) {
            try { BackendCatalogRemoteDataSource(Fake(body)).products(token); fail("Accepted invalid page") }
            catch (error: BackendCatalogException) { assertEquals(502, error.status) }
        }
        val fake = Fake("""{"items":[$product],"next_after_id":null}""")
        try { BackendCatalogRemoteDataSource(fake).products(token, categoryId = 9); fail("Accepted wrong category") } catch (error: BackendCatalogException) { assertEquals(502, error.status) }
        try { BackendCatalogRemoteDataSource(fake).products(token, afterId = 2); fail("Accepted stale cursor") } catch (error: BackendCatalogException) { assertEquals(502, error.status) }
    }
    @Test fun invalidInputDoesNotSendRequest() = runTest {
        val fake = Fake("{}"); val source = BackendCatalogRemoteDataSource(fake)
        for (action in listOf<suspend () -> Unit>({ source.products(token, limit = 101) }, { source.products(token, afterId = 0) },
            { source.products(token, search = "\uD800") }, { source.scan(token, "a\n") }, { source.scan(token, "a") })) {
            try { action(); fail("Accepted invalid input") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, fake.calls)
    }
    @Test fun errorsAreSafeWithoutRetriesAndCancellationPropagates() = runTest {
        val fake = Fake("sensitive response"); fake.status = 409
        try { BackendCatalogRemoteDataSource(fake).scan(token, "demo"); fail("Accepted ambiguous code") }
        catch (error: BackendCatalogException) { assertEquals(409, error.status); assertFalse(error.message!!.contains(fake.body)) }
        assertEquals(1, fake.calls)
        fake.error = CancellationException("cancelled")
        try { BackendCatalogRemoteDataSource(fake).products(token); fail("Swallowed cancellation") } catch (_: CancellationException) { }
    }
}
