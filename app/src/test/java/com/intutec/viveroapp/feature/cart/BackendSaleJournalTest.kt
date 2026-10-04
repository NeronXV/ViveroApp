package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.feature.cart.data.local.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.cart.sync.RoomBackendSaleOutboxStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class BackendSaleJournalTest {
    private fun header() = BackendSaleAttemptEntity(1, "a".repeat(64), 2, 3, 900, "SYNCED", 5, "VD-" + "A".repeat(24), "SENT_TO_CASHIER")
    @Test fun journalPreservesConfirmedReceiptAndOrdersIntegerLines() = runTest {
        val dao = mock(BackendSaleAttemptDao::class.java)
        `when`(dao.journal(2, 3)).thenReturn(listOf(BackendSaleAttemptWithItems(header(), listOf(BackendSaleAttemptItemEntity(1, 9, 1), BackendSaleAttemptItemEntity(1, 4, 2)))))
        val row = RoomBackendSaleOutboxStore(dao).journal(BackendSaleIdentity(2, 3)).single()
        assertEquals(5L, row.receipt?.saleId); assertEquals(900L, row.totalCents)
        assertEquals(listOf(4L, 9L), row.items.map { it.productId }); verify(dao).journal(2, 3)
    }
    @Test fun incompleteReceiptNeverAppearsConfirmed() = runTest {
        val dao = mock(BackendSaleAttemptDao::class.java)
        for (bad in listOf(header().copy(serverSaleId = null), header().copy(serverFolio = "wrong"), header().copy(serverStatus = "UNKNOWN"), header().copy(state = "UNCERTAIN"))) {
            `when`(dao.journal(2, 3)).thenReturn(listOf(BackendSaleAttemptWithItems(bad, listOf(BackendSaleAttemptItemEntity(1, 4, 2)))))
            try { RoomBackendSaleOutboxStore(dao).journal(BackendSaleIdentity(2, 3)); fail("Displayed invalid receipt") } catch (_: IllegalStateException) { }
        }
    }
    @Test fun retiredAttemptRemainsVisibleWithoutInventedReceipt() = runTest {
        val dao = mock(BackendSaleAttemptDao::class.java)
        val retired = header().copy(state = "RETIRED", serverSaleId = null, serverFolio = null, serverStatus = null)
        `when`(dao.journal(2, 3)).thenReturn(listOf(BackendSaleAttemptWithItems(retired, listOf(BackendSaleAttemptItemEntity(1, 4, 2)))))
        val row = RoomBackendSaleOutboxStore(dao).journal(BackendSaleIdentity(2, 3)).single()
        assertEquals("RETIRED", row.state); assertNull(row.receipt); assertEquals(900L, row.totalCents)
    }
}
