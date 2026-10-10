package com.intutec.viveroapp.core.network

import com.intutec.viveroapp.BuildConfig
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.activeBackend
import com.intutec.viveroapp.feature.home.presentation.backendDashboard
import io.ktor.http.HttpMethod
import org.junit.Assert.*
import org.junit.Test

class AndroidOperationalSafetyTest {
    @Test fun disabledNativeOperationsCannotSendCommercialOrInventoryMutations() {
        for (path in listOf("/api/v1/inventory/counts", "/api/v1/inventory/receptions",
            "/api/v1/inventory/count-observations", "/api/v1/inventory/activation",
            "/api/v1/sales", "/api/v1/sales/submissions", "/api/v1/cashier/payments", "/api/v1/cataloging")) {
            assertFalse(path, backendNativeRequestAllowed(path, HttpMethod.Post, false))
        }
        assertFalse(backendNativeRequestAllowed("/api/v1/products/1", HttpMethod.Patch, false))
        assertFalse(backendNativeRequestAllowed("/api/v1/cataloging/1/photo", HttpMethod.Put, false))
    }

    @Test fun mainAppKeepsLoginReadOnlyLookupAndHistoricInventoryResultRecovery() {
        for (path in listOf("/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/recovery",
            "/api/v1/auth/change-password", "/api/v1/products/scan", "/api/v1/inventory/counts/result"))
            assertTrue(path, backendNativeRequestAllowed(path, HttpMethod.Post, false))
        assertTrue(backendNativeRequestAllowed("/api/v1/auth/me", HttpMethod.Get, false))
        assertTrue(backendNativeRequestAllowed("/api/v1/inventory/dashboard", HttpMethod.Get, false))
        assertFalse(backendNativeRequestAllowed("/api/v1/auth/login/other", HttpMethod.Post, false))
    }

    @Test fun defaultDashboardHidesSalesAndCashierWithoutChangingServerPermissions() {
        val store=SessionStore()
        activeBackend(store, permissions=setOf("VIEW_CATALOG","CREATE_SALES","OPERATE_CASHIER","MANAGE_PRODUCTS","MANAGE_INVENTORY"))
        val dashboard=requireNotNull(backendDashboard(store.backend.value,true))
        assertFalse(dashboard.canCreateSales)
        assertEquals(setOf("catalog","products","inventory"),dashboard.modules.map { it.id }.toSet())
        assertEquals("Catálogo",dashboard.modules.first { it.id=="catalog" }.title)
        assertTrue("CREATE_SALES" in store.backend.value.context!!.capabilities)
    }

    @Test fun normalBuildRequiresProductionHttpsAndHasNoSupabaseCredentials() {
        assertFalse(BuildConfig.NATIVE_OPERATIONS_ENABLED)
        assertTrue(BuildConfig.BUILD_TYPE in setOf("debug","release"))
        assertEquals("https://viverodulcinea.bajastack.network",backendApiOrigin(BuildConfig.BACKEND_API_URL,false))
        assertEquals(BuildConfig.BACKEND_API_URL,BuildConfig.BACKEND_WEB_URL)
        assertEquals("",BuildConfig.SUPABASE_URL)
        assertEquals("",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
    }
}
