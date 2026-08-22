package com.intutec.viveroapp.feature.cashier

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierRemoteContractTest {
    @Test
    fun `remote data source is select only and filters branch status and detail id`() {
        val source = File(
            "src/main/java/com/intutec/viveroapp/feature/cashier/data/remote/SupabaseCashierRemoteDataSource.kt",
        ).readText()

        assertTrue(source.contains("from(\"sales\").select"))
        assertTrue(source.contains("eq(\"branch_id\", branchId)"))
        assertTrue(source.contains("eq(\"status\", CASHIER_STATUS)"))
        assertTrue(source.contains("eq(\"id\", orderId)"))
        assertTrue(source.contains("order(\"created_at\", Order.ASCENDING)"))
        listOf(".insert(", ".update(", ".delete(", ".upsert(", ".rpc(").forEach {
            assertFalse("Caja no debe contener $it", source.contains(it))
        }
    }
}
