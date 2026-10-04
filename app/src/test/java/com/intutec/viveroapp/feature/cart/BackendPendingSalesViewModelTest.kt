package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.cart.presentation.BackendPendingSalesViewModel
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackendPendingSalesViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()
    private val row = BackendSaleJournalEntry(1, "UNCERTAIN", 900, listOf(BackendSaleLine(4, 2)), "Sin confirmar", null)
    private class Fake : BackendSaleEmitter {
        var rows = emptyList<BackendSaleJournalEntry>(); var checks = 0; var retries = 0; var retires = 0; var error = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun quote(items: List<BackendSaleLine>): PreparedBackendSale = error("not used")
        override suspend fun quoteCart(cart: BackendCartSnapshot): PreparedBackendSale = error("not used")
        override suspend fun send(prepared: PreparedBackendSale): BackendSaleEmission = error("not used")
        override suspend fun pending() = rows.map { it.localId }
        override suspend fun journal(): List<BackendSaleJournalEntry> { gate?.await(); if (error) error("sensitive remote content"); return rows }
        override suspend fun checkResult(localId: Long): BackendSaleEmission { checks++; gate?.await(); return BackendSaleEmission(localId, BackendSaleEmissionOutcome.Pending("Se conserva el intento.")) }
        override suspend fun resume(localId: Long): BackendSaleEmission { retries++; return BackendSaleEmission(localId, BackendSaleEmissionOutcome.Synced) }
        override suspend fun retire(localId: Long): BackendSaleEmission { retires++; gate?.await(); rows = rows.map { if (it.localId == localId) it.copy(state = "RETIRED") else it }; return BackendSaleEmission(localId, BackendSaleEmissionOutcome.Retired) }
    }
    private fun activate(store: SessionStore, capability: Boolean = true) {
        store.beginBackendChange(BackendSessionState(BackendSession("t".repeat(43), 2, System.currentTimeMillis() + 60000),
            BackendAccessContext(BackendUser(2, "demo@example.invalid", "Demo"), "ACTIVE", BackendRole(1, UserRole.SALES, "Ventas"), BackendBranch(3, "DEMO", "Demo", true), if (capability) setOf("CREATE_SALES") else emptySet()),
            BackendAuthStatus.AUTHENTICATED, BackendAccessStatus.READY))
    }
    @Test fun legacySessionDoesNotConstructApiConsumer() = runTest(dispatcherRule.testDispatcher) {
        var resolutions = 0; val store = SessionStore()
        val vm = BackendPendingSalesViewModel(store, Provider { resolutions++; Fake() })
        testScheduler.runCurrent(); assertFalse(vm.state.value.enabled); assertEquals(0, resolutions)
        activate(store, capability = false); testScheduler.runCurrent(); assertFalse(vm.state.value.enabled); assertEquals(0, resolutions)
    }
    @Test fun loadsEntriesAndClearsThemImmediatelyOnLogout() = runTest(dispatcherRule.testDispatcher) {
        val store = SessionStore(); activate(store); val fake = Fake(); fake.rows = listOf(row)
        val vm = BackendPendingSalesViewModel(store, Provider { fake }); testScheduler.runCurrent()
        assertEquals(listOf(row), vm.state.value.entries); assertTrue(vm.state.value.enabled)
        store.clearBackend(); testScheduler.runCurrent(); assertTrue(vm.state.value.entries.isEmpty()); assertFalse(vm.state.value.enabled)
    }
    @Test fun duplicateActionsAreIgnoredAndCheckDoesNotRetry() = runTest(dispatcherRule.testDispatcher) {
        val store = SessionStore(); activate(store); val fake = Fake(); fake.rows = listOf(row)
        val vm = BackendPendingSalesViewModel(store, Provider { fake }); testScheduler.runCurrent()
        val gate = CompletableDeferred<Unit>(); fake.gate = gate
        vm.checkResult(1); testScheduler.runCurrent(); vm.checkResult(1); vm.retry(1); testScheduler.runCurrent()
        assertEquals(1, fake.checks); assertEquals(0, fake.retries)
        gate.complete(Unit); testScheduler.runCurrent(); assertFalse(vm.state.value.working)
    }
    @Test fun refreshErrorsAreSafeAndHideOldEntries() = runTest(dispatcherRule.testDispatcher) {
        val store = SessionStore(); activate(store); val fake = Fake(); fake.rows = listOf(row)
        val vm = BackendPendingSalesViewModel(store, Provider { fake }); testScheduler.runCurrent()
        fake.error = true; vm.refresh(); testScheduler.runCurrent()
        assertTrue(vm.state.value.entries.isEmpty()); assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.error!!.contains("sensitive"))
    }
    @Test fun cancelledQueryCannotRepopulateEntriesAfterLogout() = runTest(dispatcherRule.testDispatcher) {
        val store = SessionStore(); activate(store); val fake = Fake(); val gate = CompletableDeferred<Unit>(); fake.gate = gate; fake.rows = listOf(row)
        val vm = BackendPendingSalesViewModel(store, Provider { fake }); testScheduler.runCurrent()
        store.clearBackend(); testScheduler.runCurrent(); gate.complete(Unit); testScheduler.runCurrent()
        assertFalse(vm.state.value.enabled); assertTrue(vm.state.value.entries.isEmpty())
    }
    @Test fun retirementUsesExclusiveActionAndTerminalRowCannotRetry() = runTest(dispatcherRule.testDispatcher) {
        val store = SessionStore(); activate(store); val fake = Fake(); fake.rows = listOf(row)
        val vm = BackendPendingSalesViewModel(store, Provider { fake }); testScheduler.runCurrent()
        val gate = CompletableDeferred<Unit>(); fake.gate = gate
        vm.retire(1); testScheduler.runCurrent(); vm.retire(1); vm.retry(1); testScheduler.runCurrent()
        assertEquals(1, fake.retires); assertEquals(0, fake.retries)
        gate.complete(Unit); testScheduler.runCurrent()
        assertEquals("RETIRED", vm.state.value.entries.single().state); assertNotNull(vm.state.value.message)
        vm.retry(1); vm.retire(1); vm.checkResult(1); testScheduler.runCurrent()
        assertEquals(1, fake.retires); assertEquals(0, fake.retries); assertEquals(0, fake.checks)
    }
}
