package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.auth.data.repository.BackendAuthRepository
import com.intutec.viveroapp.feature.auth.data.remote.*
import com.intutec.viveroapp.feature.auth.presentation.BackendAuthViewModel
import com.intutec.viveroapp.core.network.*
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.cart.presentation.BackendCartViewModel
import com.intutec.viveroapp.feature.catalog.domain.repository.*
import com.intutec.viveroapp.feature.catalog.presentation.BackendCatalogViewModel
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal fun activeBackend(store: SessionStore, actor: Long = 2, branch: Long = 3, permissions: Set<String> = setOf("VIEW_CATALOG", "SCAN_PRODUCTS", "CREATE_SALES", "OPERATE_CASHIER")) {
    store.beginBackendChange(BackendSessionState(BackendSession("t".repeat(43), actor, System.currentTimeMillis() + 3600000),
        BackendAccessContext(BackendUser(actor, "demo@example.invalid", "Demo"), "ACTIVE", BackendRole(1, UserRole.OWNER, "Dueño"), BackendBranch(branch, "DEMO", "Demo", true), permissions),
        BackendAuthStatus.AUTHENTICATED, BackendAccessStatus.READY))
}
class BackendWorkflowTest {
    @get:Rule val main = MainDispatcherRule()
    private val product = BackendCatalogProduct(4, "DEMO", null, "Demo", null, "Demo", 1, 500, 500, "pieza", "", "", "", null, null)
    private class Cart : BackendCartRepository {
        var adds = 0
        override fun observe(identity: BackendSaleIdentity) = flowOf(BackendCartSnapshot(1, 1, identity, listOf(BackendCartLine(4, "Demo", "pieza", 500, 1))))
        override suspend fun add(product: BackendCatalogProduct) { adds++ }
        override suspend fun quantity(productId: Long, quantity: Int) = Unit
        override suspend fun remove(productId: Long) = Unit
        override suspend fun clear() = Unit
    }
    private inner class Catalog : BackendCatalogGateway {
        var calls = 0; var gate: CompletableDeferred<Unit>? = null
        override suspend fun categories(token: String, limit: Int, afterId: Long?) = BackendCatalogPage(listOf(BackendCategory(1, "Demo", "")), null)
        override suspend fun products(token: String, limit: Int, afterId: Long?, search: String, categoryId: Long?): BackendCatalogPage<BackendCatalogProduct> {
            calls++; gate?.await(); return BackendCatalogPage(listOf(product), null)
        }
        override suspend fun scan(token: String, code: String) = product
    }
    private class AuthRemote : BackendAuthRemoteDataSource {
        var logins = 0; var gate: CompletableDeferred<Unit>? = null
        override suspend fun login(email: String, password: String): BackendLogin { logins++; gate?.await(); return BackendLogin("t".repeat(43), 3600) }
        override suspend fun context(token: String, expectedUserId: Long?): BackendAccessContext {
            val store = SessionStore(); activeBackend(store); return requireNotNull(store.backend.value.context)
        }
        override suspend fun changePassword(token: String, current: String, next: String) { }
        override suspend fun logout(token: String) = Unit
    }
    @Test fun loginUsesApiContextAndLogoutClearsItWithoutLegacyIdentity() = runTest(main.testDispatcher) {
        val store = SessionStore(); val remote = AuthRemote(); val repo = BackendAuthRepository(remote, store)
        val vm = BackendAuthViewModel(Provider { repo }, Provider { error("not used") }, store)
        vm.onEmailChanged("demo@example.invalid"); vm.onPasswordChanged("synthetic-password"); vm.signIn(); testScheduler.runCurrent()
        assertEquals(2L, vm.uiState.value.backend.context?.user?.id); assertNull(store.session.value); assertEquals("", vm.uiState.value.password)
        vm.signOut(); testScheduler.runCurrent(); assertNull(vm.uiState.value.backend.session); assertNull(store.session.value)
    }
    @Test fun duplicateLoginDoesNotSendAnotherRequest() = runTest(main.testDispatcher) {
        val store = SessionStore(); val remote = AuthRemote(); val gate = CompletableDeferred<Unit>(); remote.gate = gate
        val vm = BackendAuthViewModel(Provider { BackendAuthRepository(remote, store) }, Provider { error("not used") }, store)
        vm.signIn(); testScheduler.runCurrent(); vm.signIn(); testScheduler.runCurrent(); assertEquals(1, remote.logins)
        gate.complete(Unit); testScheduler.runCurrent(); vm.signOut(); testScheduler.runCurrent()
    }
    @Test fun badApiConfigurationNeverConstructsLegacyProvider() = runTest(main.testDispatcher) {
        val vm = BackendAuthViewModel(Provider { error("Invalid public URL") }, Provider { error("not used") }, SessionStore())
        assertFalse(vm.uiState.value.remoteConfigured); assertFalse(vm.uiState.value.errorMessage!!.contains("Invalid"))
    }
    @Test fun recoveryUsesOnlyOfficialAnonymousEndpoint() = runTest(main.testDispatcher) {
        var count = 0
        val transport = object : BackendApiTransport {
            override suspend fun recovery(body: String): BackendApiResponse { count++; assertTrue(body.contains("demo@example.invalid")); return BackendApiResponse(202, "{\"accepted\":true}") }
            override suspend fun post(path: String, token: String, headers: Map<String, String>, body: String): BackendApiResponse = error("not used")
        }
        val store = SessionStore(); val vm = BackendAuthViewModel(Provider { BackendAuthRepository(AuthRemote(), store) }, Provider { transport }, store)
        vm.onEmailChanged("demo@example.invalid"); vm.sendPasswordReset(); vm.sendPasswordReset(); testScheduler.runCurrent()
        assertEquals(1, count); assertNotNull(vm.uiState.value.infoMessage)
    }
    @Test fun catalogClearsOnLogoutAndNeverRestoresDelayedResponse() = runTest(main.testDispatcher) {
        val store = SessionStore(); activeBackend(store); val remote = Catalog(); val gate = CompletableDeferred<Unit>(); remote.gate = gate
        val vm = BackendCatalogViewModel(store, remote, Cart(), "https://api.example.invalid"); testScheduler.runCurrent()
        store.clearBackend(); testScheduler.runCurrent(); gate.complete(Unit); testScheduler.runCurrent()
        assertFalse(vm.state.value.enabled); assertTrue(vm.state.value.products.isEmpty())
    }
    @Test fun consultationModeBlocksCartEditsAndRemovingCapabilitiesClearsCatalog() = runTest(main.testDispatcher) {
        val store = SessionStore(); activeBackend(store); val remote = Catalog(); val cart = Cart()
        val vm = BackendCatalogViewModel(store, remote, cart, "https://api.example.invalid"); testScheduler.runCurrent()
        assertEquals(listOf(product), vm.state.value.products); assertFalse(vm.state.value.canSell)
        vm.add(product); testScheduler.runCurrent(); assertEquals(0, cart.adds)
        activeBackend(store, permissions = emptySet()); testScheduler.runCurrent(); vm.add(product); testScheduler.runCurrent()
        assertFalse(vm.state.value.enabled); assertTrue(vm.state.value.products.isEmpty()); assertEquals(0, cart.adds)
    }
    @Test fun accessGateRejectsExpiredWrongContextAndMissingBranch() {
        val store = SessionStore(); activeBackend(store)
        val state = store.backend.value
        assertNotNull(state.authorizedSession("CREATE_SALES", true))
        assertNull(state.authorizedSession("CREATE_SALES", true, Long.MAX_VALUE))
        assertNull(state.copy(accessStatus = BackendAccessStatus.ERROR).authorizedSession("VIEW_CATALOG"))
        assertNull(state.copy(context = state.context!!.copy(user = state.context.user.copy(id = 9))).authorizedSession("VIEW_CATALOG"))
        assertNull(state.copy(context = state.context.copy(branch = null)).authorizedSession("CREATE_SALES", true))
    }
    @Test fun cancelledCartQuoteCannotReappearForAnotherSession() = runTest(main.testDispatcher) {
        val store = SessionStore(); activeBackend(store); val gate = CompletableDeferred<Unit>()
        val emitter = object : BackendSaleEmitter {
            override suspend fun quoteCart(cart: BackendCartSnapshot): PreparedBackendSale {
                gate.await(); return PreparedBackendSale(BackendSaleQuote(3, 500, 0, 500, emptyList()), BackendSaleAttempt.create(cart.identity, cart.saleLines(), 500), cart)
            }
            override suspend fun quote(items: List<BackendSaleLine>): PreparedBackendSale = error("not used")
            override suspend fun send(prepared: PreparedBackendSale): BackendSaleEmission = error("not used")
            override suspend fun resume(localId: Long): BackendSaleEmission = error("not used")
            override suspend fun pending() = emptyList<Long>()
            override suspend fun journal() = emptyList<BackendSaleJournalEntry>()
            override suspend fun checkResult(localId: Long): BackendSaleEmission = error("not used")
            override suspend fun retire(localId: Long): BackendSaleEmission = error("not used")
        }
        val vm = BackendCartViewModel(store, Cart(), emitter); testScheduler.runCurrent(); vm.quote(); testScheduler.runCurrent()
        store.clearBackend(); testScheduler.runCurrent(); gate.complete(Unit); testScheduler.runCurrent()
        assertNull(vm.state.value.cart); assertNull(vm.state.value.quote); assertFalse(vm.state.value.enabled)
    }
}
