package com.intutec.viveroapp.feature.catalog

import androidx.lifecycle.ViewModelStore
import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendCartRepository
import com.intutec.viveroapp.feature.catalog.domain.repository.*
import com.intutec.viveroapp.feature.catalog.presentation.BackendCatalogViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class BackendCatalogViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val store = ViewModelStore()
    private val sessions = SessionStore()
    private val product = BackendCatalogProduct(2, "000012", "000012", "Planta de prueba", null, "", 1,
        1000, 1000, "maceta", "", "", "", null, null)
    private val cart = Mockito.mock(BackendCartRepository::class.java)
    private class Remote : BackendCatalogGateway {
        val codes = mutableListOf<String>()
        var scanResult: suspend (String) -> BackendCatalogProduct? = { null }
        override suspend fun categories(token: String, limit: Int, afterId: Long?) = BackendCatalogPage<BackendCategory>(emptyList(), null)
        override suspend fun products(token: String, limit: Int, afterId: Long?, search: String, categoryId: Long?) = BackendCatalogPage<BackendCatalogProduct>(emptyList(), null)
        override suspend fun scan(token: String, code: String): BackendCatalogProduct? {
            codes += code
            return scanResult(code)
        }
    }
    private fun signIn(capabilities: Set<String> = setOf("VIEW_CATALOG", "SCAN_PRODUCTS", "CREATE_SALES"), userId: Long = 1) {
        sessions.beginBackendChange(BackendSessionState(
            session = BackendSession("a".repeat(43), userId, Long.MAX_VALUE),
            context = BackendAccessContext(BackendUser(userId, "synthetic@example.invalid", "Prueba"), "ACTIVE",
                BackendRole(1, UserRole.OWNER, "Propietario"), BackendBranch(1, "TEST", "Prueba", true), capabilities),
            status = BackendAuthStatus.AUTHENTICATED, accessStatus = BackendAccessStatus.READY,
        ))
    }
    private fun viewModel(remote: Remote): BackendCatalogViewModel {
        return BackendCatalogViewModel(sessions, remote, cart, "https://example.invalid").also { store.put("catalog", it) }
    }
    @After fun clear() = store.clear()

    @Test fun scannedProductIsDisplayedWithoutAddingItAutomaticallyToCart() = runTest {
        signIn()
        val remote = Remote().apply { scanResult = { product } }
        val vm = viewModel(remote)
        runCurrent()
        vm.scan("000012")
        runCurrent()
        assertEquals(listOf("000012"), remote.codes)
        assertEquals(listOf(product), vm.state.value.products)
        assertFalse(vm.state.value.loading)
        Mockito.verifyNoInteractions(cart)
    }

    @Test fun scanDoesNotConsultApiWithoutCapabilityEvenForOwner() = runTest {
        signIn(setOf("VIEW_CATALOG"))
        val remote = Remote()
        val vm = viewModel(remote)
        runCurrent()
        vm.scan("000012")
        runCurrent()
        assertTrue(remote.codes.isEmpty())
        assertFalse(vm.state.value.canScan)
        Mockito.verifyNoInteractions(cart)
    }

    @Test fun newScanClearsThePreviousNotFoundMessage() = runTest {
        signIn()
        val remote = Remote()
        val vm = viewModel(remote)
        runCurrent()
        vm.scan("unknown")
        runCurrent()
        assertNotNull(vm.state.value.message)
        remote.scanResult = { product }
        vm.scan("000012")
        assertNull(vm.state.value.message)
        runCurrent()
        assertNull(vm.state.value.message)
        assertEquals(listOf(product), vm.state.value.products)
    }

    @Test fun lateScanCannotReplaceANewerNameSearch() = runTest {
        signIn()
        val pending = CompletableDeferred<BackendCatalogProduct?>()
        val remote = Remote().apply { scanResult = { withContext(NonCancellable) { pending.await() } } }
        val vm = viewModel(remote)
        runCurrent()
        vm.scan("000012")
        runCurrent()
        vm.search("otra planta")
        pending.complete(product)
        runCurrent()
        assertTrue(vm.state.value.products.isEmpty())
        assertEquals("otra planta", vm.state.value.query)
    }

    @Test fun signingOutDiscardsAnInFlightScanAndDisablesCameraPermission() = runTest {
        signIn()
        val pending = CompletableDeferred<BackendCatalogProduct?>()
        val remote = Remote().apply { scanResult = { withContext(NonCancellable) { pending.await() } } }
        val vm = viewModel(remote)
        runCurrent()
        vm.scan("000012")
        runCurrent()
        sessions.clearBackend()
        runCurrent()
        pending.complete(product)
        runCurrent()
        assertFalse(vm.state.value.enabled)
        assertFalse(vm.state.value.canScan)
        assertTrue(vm.state.value.products.isEmpty())
        Mockito.verifyNoInteractions(cart)
    }
}
