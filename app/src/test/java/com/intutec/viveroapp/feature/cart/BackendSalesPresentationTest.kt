package com.intutec.viveroapp.feature.cart

import androidx.lifecycle.ViewModelStore
import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.cart.presentation.BackendCartViewModel
import com.intutec.viveroapp.feature.catalog.domain.repository.*
import com.intutec.viveroapp.feature.catalog.presentation.BackendCatalogViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BackendSalesPresentationTest {
    @get:Rule val main = MainDispatcherRule()
    private val sessions = SessionStore()
    private val store = ViewModelStore()
    private val original = BackendCartSnapshot(1, 1, BackendSaleIdentity(2,3), listOf(BackendCartLine(4,"Monstera","maceta",25000,1)))
    private class Cart(snapshot: BackendCartSnapshot) : BackendCartRepository {
        val rows = MutableStateFlow<BackendCartSnapshot?>(snapshot)
        override fun observe(identity: BackendSaleIdentity) = rows
        override suspend fun add(product: BackendCatalogProduct) = Unit
        override suspend fun quantity(productId: Long, quantity: Int) = Unit
        override suspend fun remove(productId: Long) = Unit
        override suspend fun clear() = Unit
    }
    private class Emitter : BackendSaleEmitter {
        var entries = emptyList<BackendSaleJournalEntry>()
        var unavailable = false; var quoteFailure: Exception? = null; var quotes = 0; var sends = 0; var checked: Long? = null
        var outcome: BackendSaleEmissionOutcome = BackendSaleEmissionOutcome.Synced
        override suspend fun journal(): List<BackendSaleJournalEntry> { check(!unavailable); return entries }
        override suspend fun pending() = entries.filter { it.state == "UNCERTAIN" }.map { it.localId }
        override suspend fun quoteCart(cart: BackendCartSnapshot): PreparedBackendSale {
            quotes++
            quoteFailure?.let { throw it }
            val quote = BackendSaleQuote(3,26000,0,26000,listOf(BackendSaleQuotedLine(4,1,"Monstera","M-4",26000,26000,26000)))
            return PreparedBackendSale(quote, BackendSaleAttempt.create(cart.identity,cart.saleLines(),26000),cart)
        }
        override suspend fun quote(items: List<BackendSaleLine>): PreparedBackendSale = error("not used")
        override suspend fun send(prepared: PreparedBackendSale): BackendSaleEmission {
            sends++
            entries = listOf(BackendSaleJournalEntry(8,if(outcome == BackendSaleEmissionOutcome.Synced) "SYNCED" else "UNCERTAIN",
                26000,prepared.attempt.items,null,if(outcome == BackendSaleEmissionOutcome.Synced) BackendStoredSaleReceipt(90,"VD-0090","SENT_TO_CASHIER") else null))
            return BackendSaleEmission(8,outcome)
        }
        override suspend fun checkResult(localId: Long): BackendSaleEmission {
            checked=localId
            entries=entries.map { it.copy(state="SYNCED",receipt=BackendStoredSaleReceipt(90,"VD-0090","SENT_TO_CASHIER")) }
            return BackendSaleEmission(localId,BackendSaleEmissionOutcome.Synced)
        }
        override suspend fun resume(localId: Long): BackendSaleEmission = error("not used")
        override suspend fun retire(localId: Long): BackendSaleEmission = error("not used")
    }
    private fun vm(cart: Cart, emitter: Emitter, catalog: BackendCatalogGateway? = null): BackendCartViewModel {
        activeBackend(sessions,permissions=setOf("CREATE_SALES","VIEW_CATALOG","VIEW_OWN_SALES"))
        return BackendCartViewModel(sessions,cart,emitter,catalog,"https://example.invalid").also { store.put("cart",it) }
    }
    @After fun clear() = store.clear()
    @Test fun successUsesConfirmedJournalFolioAndTotalRatherThanTheDraft() = runTest(main.testDispatcher) {
        val emitter=Emitter(); val vm=vm(Cart(original),emitter); testScheduler.runCurrent()
        vm.quote(); testScheduler.runCurrent(); assertEquals(26000L,vm.state.value.quote?.totalCents)
        vm.send(); testScheduler.runCurrent()
        assertEquals("VD-0090",vm.state.value.sent?.receipt?.folio); assertEquals(26000L,vm.state.value.sent?.totalCents)
        assertEquals(1,emitter.sends); assertTrue(vm.state.value.canViewHistory)
        vm.newSale(); assertNull(vm.state.value.sent)
    }
    @Test fun lostResponseBlocksNewQuoteAndRecoversTheOriginalLocalId() = runTest(main.testDispatcher) {
        val emitter=Emitter().apply { outcome=BackendSaleEmissionOutcome.Pending("Sin respuesta") }
        val vm=vm(Cart(original),emitter); testScheduler.runCurrent(); vm.quote(); testScheduler.runCurrent(); vm.send(); testScheduler.runCurrent()
        assertEquals(8L,vm.state.value.unresolved.single().localId)
        vm.quote(); testScheduler.runCurrent(); assertEquals(1,emitter.quotes)
        vm.checkResult(8); testScheduler.runCurrent()
        assertEquals(8L,emitter.checked); assertEquals(1,emitter.sends); assertEquals("VD-0090",vm.state.value.sent?.receipt?.folio)
    }
    @Test fun reopenedViewModelShowsPersistedPendingWithoutCreatingAnAttempt() = runTest(main.testDispatcher) {
        val emitter=Emitter().apply { entries=listOf(BackendSaleJournalEntry(8,"UNCERTAIN",26000,original.saleLines(),null,null)) }
        val vm=vm(Cart(original),emitter); testScheduler.runCurrent()
        assertEquals(8L,vm.state.value.unresolved.single().localId); vm.quote(); testScheduler.runCurrent()
        assertEquals(0,emitter.quotes); assertEquals(0,emitter.sends)
    }
    @Test fun unreadableJournalBlocksSendingAndCanBeReadAgain() = runTest(main.testDispatcher) {
        val emitter=Emitter().apply { unavailable=true }; val vm=vm(Cart(original),emitter); testScheduler.runCurrent()
        assertFalse(vm.state.value.journalReady); assertNotNull(vm.state.value.error)
        vm.quote(); testScheduler.runCurrent(); assertEquals(0,emitter.quotes)
        emitter.unavailable=false; vm.refreshJournal(); testScheduler.runCurrent(); assertTrue(vm.state.value.journalReady)
    }
    @Test fun aServerValidationErrorPreservesDraftWithoutSending() = runTest(main.testDispatcher) {
        val emitter=Emitter().apply { quoteFailure=BackendSaleException(409,"INSUFFICIENT_STOCK") }
        val vm=vm(Cart(original),emitter);testScheduler.runCurrent();vm.quote();testScheduler.runCurrent()
        assertNotNull(vm.state.value.error);assertEquals(original,vm.state.value.cart)
        assertNull(vm.state.value.quote);assertEquals(0,emitter.sends)
    }
    @Test fun photoLookupNeverRepricesCartAndRevocationClearsPresentation() = runTest(main.testDispatcher) {
        var calls=0
        val catalog=object : BackendCatalogGateway {
            override suspend fun categories(token: String,limit: Int,afterId: Long?)=BackendCatalogPage<BackendCategory>(emptyList(),null)
            override suspend fun products(token: String,limit: Int,afterId: Long?,search: String,categoryId: Long?): BackendCatalogPage<BackendCatalogProduct> {
                calls++;return BackendCatalogPage(listOf(BackendCatalogProduct(4,"M-4",null,"Monstera",null,"",1,99999,99999,"maceta","","","",BackendCatalogImage(2,"/api/v1/images/2","Monstera"),null)),null)
            }
            override suspend fun scan(token: String,code: String): BackendCatalogProduct? = null
        }
        val cart=Cart(original); val vm=vm(cart,Emitter(),catalog); testScheduler.runCurrent()
        assertEquals(25000L,vm.state.value.cart?.items?.single()?.priceCents)
        assertEquals("https://example.invalid/api/v1/images/2",vm.state.value.photos[4])
        cart.rows.value=original.copy(revision=2,items=original.items.map { it.copy(quantity=2) });testScheduler.runCurrent();assertEquals(1,calls)
        sessions.clearBackend();testScheduler.runCurrent();assertTrue(vm.state.value.photos.isEmpty());assertFalse(vm.state.value.canViewHistory)
    }
    @Test fun searchAndCategoryUseTheExistingRemoteFilters() = runTest(main.testDispatcher) {
        val calls=mutableListOf<Pair<String,Long?>>()
        val product=BackendCatalogProduct(4,"M-4",null,"Monstera",null,"",1,25000,25000,"maceta","","","",null,null)
        val remote=object : BackendCatalogGateway {
            override suspend fun categories(token: String,limit: Int,afterId: Long?)=BackendCatalogPage(listOf(BackendCategory(1,"Plantas","")),null)
            override suspend fun products(token: String,limit: Int,afterId: Long?,search: String,categoryId: Long?): BackendCatalogPage<BackendCatalogProduct> {
                calls+=search to categoryId
                return BackendCatalogPage(if(categoryId==null || categoryId==1L) listOf(product) else emptyList(),null)
            }
            override suspend fun scan(token: String,code: String): BackendCatalogProduct? = null
        }
        activeBackend(sessions)
        val vm=BackendCatalogViewModel(sessions,remote,Cart(original),"https://example.invalid").also { store.put("catalog",it) }
        testScheduler.runCurrent();vm.search("Monstera");testScheduler.advanceTimeBy(300);testScheduler.runCurrent()
        assertEquals("Monstera" to null,calls.last());assertEquals(listOf(product),vm.state.value.products)
        vm.category(2);testScheduler.runCurrent();assertEquals("Monstera" to 2L,calls.last());assertTrue(vm.state.value.products.isEmpty())
        activeBackend(sessions,branch=4);testScheduler.runCurrent()
        assertEquals("" to null,calls.last());assertEquals("Demo",vm.state.value.branchName)
    }
}
