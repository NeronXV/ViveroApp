package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.core.di.BackendNetworkModule
import com.intutec.viveroapp.core.network.KtorBackendApiTransport
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.auth.data.remote.ApiBackendAuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.data.repository.BackendAuthRepository
import com.intutec.viveroapp.feature.catalog.data.remote.BackendCatalogRemoteDataSource
import com.intutec.viveroapp.feature.cart.data.remote.BackendSaleRemoteDataSource
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.cashier.data.remote.BackendCashierRemoteDataSource
import com.intutec.viveroapp.feature.mysales.data.remote.BackendHistoryRemoteDataSource
import com.intutec.viveroapp.feature.mysales.domain.repository.BackendHistoryKind
import com.intutec.viveroapp.feature.cashier.domain.repository.BackendPaymentRetirement
import com.intutec.viveroapp.feature.inventory.data.remote.BackendInventoryRemoteDataSource
import com.intutec.viveroapp.feature.inventory.domain.repository.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BackendAndroidHttpIntegrationTest {
    @Test fun realKotlinHttpLoginCatalogSalePaymentRecoveryAndLogout() = runBlocking {
        val path = System.getenv("VIVERO_ANDROID_HTTP_FIXTURE")
        assumeTrue("Requires own synthetic local fixture", !path.isNullOrBlank())
        val origin = System.getenv("VIVERO_ANDROID_HTTP_ORIGIN")
        val fixture = Json.parseToJsonElement(File(requireNotNull(path)).readText()).jsonObject
        val isolatedVps = origin == "http://127.0.0.1:38003" &&
            fixture["project"]?.jsonPrimitive?.content == "vivero-acceptance-20261003" &&
            System.getenv("VIVERO_ANDROID_HTTP_ACCEPTANCE_PROJECT") == "vivero-acceptance-20261003"
        require(origin == "http://127.0.0.1:33003" || isolatedVps) {
            "Only isolated local Compose or the explicitly acknowledged VPS acceptance tunnel is accepted"
        }
        val client = BackendNetworkModule.client()
        val transport = KtorBackendApiTransport(client, origin)
        val store = SessionStore()
        val auth = BackendAuthRepository(ApiBackendAuthRemoteDataSource(transport), store)
        try {
            assertTrue(auth.signIn(fixture["email"]!!.jsonPrimitive.content, fixture["password"]!!.jsonPrimitive.content).isSuccess)
            val session = requireNotNull(store.backend.value.session)
            val context = requireNotNull(store.backend.value.context)
            val identity = BackendSaleIdentity(session.userId, requireNotNull(context.branch).id)
            assertEquals(fixture["userId"]!!.jsonPrimitive.long, identity.userId)
            assertEquals(fixture["branchId"]!!.jsonPrimitive.long, identity.branchId)
            assertNull(store.session.value)
            val catalog = BackendCatalogRemoteDataSource(transport)
            val code = fixture["code"]!!.jsonPrimitive.content
            val productName = fixture["productName"]?.jsonPrimitive?.content ?: "Planta sintetica Android"
            val product = catalog.products(session.token, 20, search = productName).items.single { it.id == fixture["productId"]!!.jsonPrimitive.long }
            assertEquals(fixture["productId"]!!.jsonPrimitive.long, product.id)
            assertEquals(product.id, catalog.scan(session.token, code)?.id)
            val inventory = BackendInventoryRemoteDataSource(transport)
            val receivedAttempt = BackendInventoryAttempt.create(identity, BackendInventoryAction.RECEPTION, product.id, 2, "Recepción sintética Android")
            val received = inventory.submit(session.token, receivedAttempt)
            assertEquals(12000L, received.totalMilli)
            assertEquals(received.id, inventory.result(session.token, receivedAttempt).id)
            val countAttempt = BackendInventoryAttempt.create(identity, BackendInventoryAction.COUNT, product.id, 10, "Conteo sintético Android")
            val counted = inventory.submit(session.token, countAttempt)
            assertEquals(-2000L, counted.adjustmentMilli)
            assertEquals(counted.id, inventory.result(session.token, countAttempt).id)
            assertEquals(10000L, inventory.dashboard(session.token, identity).items.single { it.id == product.id }.quantityMilli)
            assertEquals(-2000L, inventory.history(session.token, identity, product.id).items.first().quantityMilli)
            val sales = BackendSaleRemoteDataSource(transport)
            val lines = listOf(BackendSaleLine(product.id, 2))
            val quote = sales.quote(session.token, identity, lines)
            assertEquals(1000L, quote.totalCents)
            val attempt = BackendSaleAttempt.create(identity, lines, quote.totalCents)
            val sale = sales.submit(session.token, attempt)
            assertEquals(sale.id, sales.recover(session.token, attempt).id)
            assertTrue(sales.retire(session.token, attempt) is BackendSaleRetirement.Committed)
            assertEquals(BackendSaleRetirement.Retired, sales.retire(session.token, BackendSaleAttempt.create(identity, lines, 1000)))
            val cashier = BackendCashierRemoteDataSource(transport)
            assertTrue(cashier.list(session.token).items.any { it.id == sale.id })
            val history = BackendHistoryRemoteDataSource(transport)
            assertTrue(history.list(session.token, identity, BackendHistoryKind.SALES).items.any { it.id == sale.id })
            assertEquals(sale.id, history.detail(session.token, identity, BackendHistoryKind.SALES, sale.id).sale.id)
            assertEquals(1000L, history.detail(session.token, identity, BackendHistoryKind.QUEUE, sale.id).lines.single().totalCents)
            val claim = cashier.claim(session.token, identity, sale.id)
            val body = buildJsonObject { put("claim_token", claim.token); put("method", "CASH"); put("amount_received_cents", 1200); put("reference", JsonNull) }.toString()
            val key = BackendSaleAttempt.create(identity, lines, 1000).key
            val paid = cashier.pay(session.token, identity, sale.id, key, body)
            assertEquals(200L, paid.changeCents)
            assertEquals(paid.id, cashier.recover(session.token, identity, sale.id, key, body).id)
            assertTrue(history.list(session.token, identity, BackendHistoryKind.PAYMENTS).items.any { it.id == paid.id })
            assertEquals(200L, history.detail(session.token, identity, BackendHistoryKind.PAYMENTS, paid.id).payment!!.changeCents)
            assertTrue(cashier.retire(session.token, identity, sale.id, key, body) is BackendPaymentRetirement.Committed)
            val closedKey = BackendSaleAttempt.create(identity, lines, 1000).key
            assertEquals(BackendPaymentRetirement.Retired, cashier.retire(session.token, identity, sale.id, closedKey, body))
            assertTrue(auth.signOut().isSuccess); assertNull(store.backend.value.session)
        } finally {
            auth.signOut(); client.close()
        }
    }
}
