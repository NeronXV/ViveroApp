package com.intutec.viveroapp.feature.cashier

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.network.BackendApiResponse
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.activeBackend
import com.intutec.viveroapp.feature.cashier.domain.repository.BackendCashierSale
import com.intutec.viveroapp.feature.cashier.presentation.BackendCashierPatioViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackendCashierPatioTest {
    @get:Rule val main = MainDispatcherRule()
    private val sale = BackendCashierSale(9, "VD-0009", 900)

    @Test fun opensScopedOrderAndReadsPatioReference() = runTest(main.testDispatcher) {
        val sessions = SessionStore().also { activeBackend(it, permissions = setOf("OPERATE_CASHIER")) }
        val transport = object : BackendApiTransport {
            override suspend fun get(path: String, token: String, query: Map<String, String>): BackendApiResponse {
                assertEquals("/api/v1/cashier/sales/9", path)
                assertEquals(mapOf("view" to "patio"), query)
                return BackendApiResponse(200, """{"schema_version":1,"sale":{"id":9,"branch_id":3,"folio":"VD-0009","total_cents":900,"status":"SENT_TO_CASHIER"},"patio_note":"Cliente del patio"}""")
            }
            override suspend fun post(path: String, token: String, headers: Map<String, String>, body: String): BackendApiResponse = error("not used")
        }
        val vm = BackendCashierPatioViewModel(sessions, transport)
        vm.open(sale); testScheduler.runCurrent()
        assertEquals("Cliente del patio", vm.state.value.note)
        assertFalse(vm.state.value.error)
    }

    @Test fun mismatchedBranchNeverPresentsNoteAsReady() = runTest(main.testDispatcher) {
        val sessions = SessionStore().also { activeBackend(it, permissions = setOf("OPERATE_CASHIER")) }
        val transport = object : BackendApiTransport {
            override suspend fun get(path: String, token: String, query: Map<String, String>) =
                BackendApiResponse(200, """{"schema_version":1,"sale":{"id":9,"branch_id":8,"folio":"VD-0009","total_cents":900,"status":"SENT_TO_CASHIER"},"patio_note":"Otra sucursal"}""")
            override suspend fun post(path: String, token: String, headers: Map<String, String>, body: String): BackendApiResponse = error("not used")
        }
        val vm = BackendCashierPatioViewModel(sessions, transport)
        vm.open(sale); testScheduler.runCurrent()
        assertTrue(vm.state.value.error)
        assertNull(vm.state.value.note)
    }
}
