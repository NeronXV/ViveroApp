package com.intutec.viveroapp.feature.cashier

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierRlsContractTest {
    @Test
    fun `cashier RLS requires assigned branch pending state and capability`() {
        val migration = File("../supabase/migrations/202608080003_sales_cart.sql").readText()
        val policy = migration.substringAfter("create policy sales_cashier_read_pending")
            .substringBefore("create policy sales_management_read_branch")

        assertTrue(policy.contains("branch_id = (select p.branch_id"))
        assertTrue(policy.contains("p.id = auth.uid()"))
        assertTrue(policy.contains("status in ('SENT_TO_CASHIER', 'PAYMENT_PENDING')"))
        assertTrue(policy.contains("public.has_permission('OPERATE_CASHIER')"))
    }

    @Test
    fun `items and history inherit visibility from their sale`() {
        val migration = File("../supabase/migrations/202608080003_sales_cart.sql").readText()

        assertTrue(migration.contains("where s.id = sale_items.sale_id"))
        assertTrue(migration.contains("where s.id = sale_status_history.sale_id"))
    }

    @Test
    fun `hardened grants keep sales tables select only for authenticated`() {
        val migration = File("../supabase/migrations/202608150001_harden_table_privileges.sql").readText()
        val writableCatalogGrant = migration.substringAfter("grant insert, update, delete on table")

        assertTrue(migration.contains("public.sale_items,\n    public.sale_status_history,\n    public.sales"))
        assertTrue(writableCatalogGrant.contains("public.categories"))
        assertTrue(!writableCatalogGrant.contains("public.sales"))
    }
}
