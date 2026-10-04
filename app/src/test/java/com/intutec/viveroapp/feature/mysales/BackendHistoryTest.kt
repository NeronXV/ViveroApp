package com.intutec.viveroapp.feature.mysales

import com.intutec.viveroapp.navigation.BackendHistoryRoute
import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.network.*
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.activeBackend
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.mysales.data.remote.BackendHistoryRemoteDataSource
import com.intutec.viveroapp.feature.mysales.domain.repository.*
import com.intutec.viveroapp.feature.mysales.presentation.BackendHistoryViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackendHistoryTest {
    @get:Rule val main = MainDispatcherRule()
    private val who = BackendSaleIdentity(2,3)
    private val sale = """{"id":5,"folio":"VD-DEMO","branch_id":3,"web_order_id":null,"status":"PAID","subtotal_cents":500,"discount_cents":0,"total_cents":500}"""
    private val ownSale = """{"id":5,"folio":"VD-DEMO","branch_id":3,"created_by":2,"status":"PAID","subtotal_cents":500,"discount_cents":0,"total_cents":500,"created_at":"2026-10-02T12:00:00Z"}"""
    private val line = """{"id":7,"product_id":8,"product_name":"Demo","internal_code":null,"quantity":"0.500","list_price_cents":null,"unit_price_cents":1000,"line_total_cents":500}"""
    private val historical = """{"schema_version":1,"sale":$sale,"items":[$line],"payment":{"id":6,"sale_id":5,"cashier_id":2,"branch_id":3,"method":"CASH","amount_due_cents":500,"amount_received_cents":600,"change_cents":100,"reference":null,"created_at":"2026-10-02T12:00:00.000Z"},"branch":{"id":3,"name":"Demo"},"refund":{"id":9,"amount_cents":500,"method":"CASH"}}"""
    private class Transport(var response: BackendApiResponse) : BackendApiTransport {
        var path = ""; var query = emptyMap<String,String>()
        override suspend fun get(path: String, token: String, query: Map<String,String>): BackendApiResponse { this.path = path; this.query = query; return response }
        override suspend fun post(path: String, token: String, headers: Map<String,String>, body: String): BackendApiResponse = error("History must never write")
    }
    @Test fun receiptPreservesDecimalQuantityHistoricalNullsAndRefundWithoutCatalog() = runTest {
        val transport = Transport(BackendApiResponse(200, historical))
        val doc = BackendHistoryRemoteDataSource(transport).detail("t".repeat(43), who, BackendHistoryKind.PAYMENTS, 6)
        assertEquals("/api/v1/cashier/receipts/6", transport.path)
        assertEquals("0.500", doc.lines.single().quantity); assertNull(doc.lines.single().code); assertNull(doc.lines.single().listPriceCents)
        assertEquals(100L, doc.payment!!.changeCents); assertEquals(500L, doc.refund!!.amountCents)
    }
    @Test fun mismatchedIdentityPaymentArithmeticQuantityAndRefundAreRejected() = runTest {
        for (wrong in listOf(historical.replace("\"cashier_id\":2", "\"cashier_id\":9"), historical.replace("\"branch_id\":3", "\"branch_id\":4"), historical.replace("\"change_cents\":100", "\"change_cents\":101"), historical.replace("0.500", "0.501"), historical.replace("\"amount_cents\":500", "\"amount_cents\":499"))) {
            try { BackendHistoryRemoteDataSource(Transport(BackendApiResponse(200, wrong))).detail("t".repeat(43), who, BackendHistoryKind.PAYMENTS, 6); fail("Invalid history") } catch (_: IllegalStateException) { }
        }
    }
    @Test fun ownSaleVerifiesOwnerHistoryAndExactStoredTotals() = runTest {
        val ownLine = line.dropLast(1) + ",\"promotion_id\":null,\"promotion_name\":null}"
        val body = """{"schema_version":1,"sale":$ownSale,"items":[$ownLine],"history":[{"previous_status":"SENT_TO_CASHIER","new_status":"PAID","observation":"Payment confirmed","created_at":"2026-10-02T12:00:00.000Z"}]}"""
        val source = BackendHistoryRemoteDataSource(Transport(BackendApiResponse(200, body)))
        assertEquals("PAID", source.detail("t".repeat(43), who, BackendHistoryKind.SALES, 5).events.single().status)
        try { source.detail("t".repeat(43), BackendSaleIdentity(9,3), BackendHistoryKind.SALES, 5); fail("Read another owner") } catch (_: IllegalStateException) { }
    }
    @Test fun pagesRejectDuplicateRowsWrongCursorAndUnsupportedSchema() = runTest {
        val item = """{"id":6,"sale_id":5,"folio":"VD-DEMO","method":"CASH","amount_due_cents":500,"created_at":"2026-10-02T12:00:00Z"}"""
        val transport = Transport(BackendApiResponse(200, """{"schema_version":1,"items":[$item],"next_before_id":null}"""))
        val source = BackendHistoryRemoteDataSource(transport)
        assertEquals(6L, source.list("t".repeat(43), who, BackendHistoryKind.PAYMENTS, 7).items.single().id)
        assertEquals("7", transport.query["before_id"])
        for (wrong in listOf("""{"schema_version":1,"items":[$item,$item],"next_before_id":null}""", """{"schema_version":1,"items":[$item],"next_before_id":6}""", """{"schema_version":2,"items":[],"next_before_id":null}""")) {
            transport.response = BackendApiResponse(200, wrong)
            try { source.list("t".repeat(43), who, BackendHistoryKind.PAYMENTS); fail("Bad page") } catch (_: IllegalStateException) { }
        }
    }
    @Test fun cashierQueueDetailCannotSubstituteAnotherSaleOrBranch() = runTest {
        val source = BackendHistoryRemoteDataSource(Transport(BackendApiResponse(200, """{"schema_version":1,"sale":$sale,"items":[$line]}""")))
        assertEquals(5L, source.detail("t".repeat(43), who, BackendHistoryKind.QUEUE, 5).sale.id)
        try { source.detail("t".repeat(43), who, BackendHistoryKind.QUEUE, 7); fail("Wrong sale") } catch (_: IllegalStateException) { }
    }
    private class Remote : BackendHistoryGateway {
        var reads = 0; var detailGate: CompletableDeferred<Unit>? = null
        override suspend fun list(token: String, identity: BackendSaleIdentity, kind: BackendHistoryKind, before: Long?): BackendHistoryPage { reads++; return BackendHistoryPage(listOf(BackendHistoryEntry(identity.userId, identity.userId,"VD-DEMO",500,"PAID","2026-10-02T12:00:00Z")),null) }
        override suspend fun detail(token: String, identity: BackendSaleIdentity, kind: BackendHistoryKind, id: Long): BackendSaleDocument {
            detailGate?.let { withContext(NonCancellable) { it.await() } }
            return BackendSaleDocument(BackendSaleSnapshot(id,"OLD-ACCOUNT",identity.branchId,"PAID",500,0,500), emptyList())
        }
    }
    private fun ownAccess(store: SessionStore, actor: Long = 2) = activeBackend(store, actor, permissions = setOf("CREATE_SALES", "VIEW_OWN_SALES"))
    @Test fun lateDetailCannotLeakIntoAnotherAccount() = runTest {
        val store = SessionStore(); ownAccess(store); val remote = Remote()
        val vm = BackendHistoryViewModel(BackendHistoryRoute(), store, remote)
        testScheduler.runCurrent(); val gate = CompletableDeferred<Unit>(); remote.detailGate = gate
        vm.detail(5); testScheduler.runCurrent()
        ownAccess(store,9); testScheduler.runCurrent(); gate.complete(Unit); testScheduler.runCurrent()
        assertNull(vm.state.value.document); assertEquals(9L, vm.state.value.entries.single().id)
    }
    @Test fun lossOfRequiredCapabilityClearsHistoryAndStopsReads() = runTest {
        val store = SessionStore(); ownAccess(store); val remote = Remote()
        val vm = BackendHistoryViewModel(BackendHistoryRoute(), store, remote)
        testScheduler.runCurrent(); assertEquals(1, remote.reads)
        activeBackend(store, permissions = setOf("VIEW_OWN_SALES")); testScheduler.runCurrent()
        vm.refresh(); testScheduler.runCurrent()
        assertFalse(vm.state.value.enabled); assertTrue(vm.state.value.entries.isEmpty()); assertEquals(1, remote.reads)
    }
}
