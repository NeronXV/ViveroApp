package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import com.intutec.viveroapp.feature.cart.domain.model.SaleStatus
import com.intutec.viveroapp.feature.cart.domain.model.SaleSyncState
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.presentation.offersMySalesNavigation
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CartSuccessNavigationTest {
    @Test
    fun `successful synced comanda offers navigation to My Sales`() {
        assertTrue(ticket(SaleSyncState.SYNCED).offersMySalesNavigation())
    }

    @Test
    fun `local pending comanda is not presented as visible in My Sales`() {
        assertFalse(ticket(SaleSyncState.PENDING).offersMySalesNavigation())
    }

    private fun ticket(syncState: SaleSyncState) = SaleTicket(
        id = "11111111-1111-4111-8111-111111111111",
        folio = "VD-260829-111111",
        items = listOf(CartItem("22222222-2222-4222-8222-222222222222", "PL-ALOE-001", "Aloe vera", "", "pieza", 12_500, 12_500, 1, 5)),
        customer = null,
        subtotalCents = 12_500,
        discountCents = 0,
        totalCents = 12_500,
        status = SaleStatus.SENT_TO_CASHIER,
        createdBy = "33333333-3333-4333-8333-333333333333",
        branchId = "44444444-4444-4444-8444-444444444444",
        createdAt = Instant.EPOCH,
        syncState = syncState,
        syncAttemptCount = 1,
        syncLastError = null,
        syncLastAttemptAt = Instant.EPOCH,
        history = emptyList(),
    )
}
