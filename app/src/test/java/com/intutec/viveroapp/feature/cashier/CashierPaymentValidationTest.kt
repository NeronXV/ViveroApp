package com.intutec.viveroapp.feature.cashier

import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentMethod
import com.intutec.viveroapp.feature.cashier.domain.model.canonicalFor
import com.intutec.viveroapp.feature.cashier.presentation.parseMxnCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CashierPaymentValidationTest {
    @Test fun `cash text maps pesos to cents without floating point`() {
        assertEquals(10_000L, "100".parseMxnCents())
        assertEquals(10_050L, "100.50".parseMxnCents())
        assertEquals(10_010L, "100.1".parseMxnCents())
        assertEquals(1L, "0.01".parseMxnCents())
        assertEquals(99_999_999_999L, "999999999.99".parseMxnCents())
        assertNull("100.001".parseMxnCents())
        assertNull("texto".parseMxnCents())
        assertNull("92233720368547759.00".parseMxnCents())
    }

    @Test fun `cash exact and cash with change preserve integer cents`() {
        assertEquals(10_000L, CashierPaymentInput(CashierPaymentMethod.CASH, 10_000).canonicalFor(10_000).amountReceivedCents)
        assertEquals(12_500L, CashierPaymentInput(CashierPaymentMethod.CASH, 12_500).canonicalFor(10_000).amountReceivedCents)
    }

    @Test fun `cash below total is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CashierPaymentInput(CashierPaymentMethod.CASH, 9_999).canonicalFor(10_000)
        }
    }

    @Test fun `card accepts no reference and a safe terminal folio`() {
        assertNull(CashierPaymentInput(CashierPaymentMethod.CARD).canonicalFor(10_000).reference)
        assertEquals("TERM-A8Z21", CashierPaymentInput(CashierPaymentMethod.CARD, reference = " TERM-A8Z21 ").canonicalFor(10_000).reference)
    }

    @Test fun `card rejects short numeric and pan shaped references`() {
        listOf("123", "1234", "4111 1111 1111 1111").forEach { reference ->
            assertThrows(IllegalArgumentException::class.java) {
                CashierPaymentInput(CashierPaymentMethod.CARD, reference = reference).canonicalFor(10_000)
            }
        }
    }

    @Test fun `transfer requires and normalizes reference`() {
        assertThrows(IllegalArgumentException::class.java) {
            CashierPaymentInput(CashierPaymentMethod.TRANSFER).canonicalFor(10_000)
        }
        assertEquals("SPEI-A1", CashierPaymentInput(CashierPaymentMethod.TRANSFER, reference = " SPEI-A1 ").canonicalFor(10_000).reference)
    }

    @Test fun `control characters and invalid totals are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CashierPaymentInput(CashierPaymentMethod.TRANSFER, reference = "A\nB").canonicalFor(10_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CashierPaymentInput(CashierPaymentMethod.CASH, 1).canonicalFor(0)
        }
    }
}
