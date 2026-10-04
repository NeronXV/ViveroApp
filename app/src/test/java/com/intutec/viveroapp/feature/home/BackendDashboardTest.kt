package com.intutec.viveroapp.feature.home

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.home.presentation.backendDashboard
import org.junit.Assert.*
import org.junit.Test

class BackendDashboardTest {
    private fun session(capabilities: Set<String>) = BackendSessionState(
        session = BackendSession("a".repeat(43), 1, Long.MAX_VALUE),
        context = BackendAccessContext(BackendUser(1, "owner@example.invalid", "Usuario de prueba"), "ACTIVE",
            BackendRole(1, UserRole.OWNER, "Propietario"), BackendBranch(1, "TEST", "Sucursal de prueba", true), capabilities),
        status = BackendAuthStatus.AUTHENTICATED, accessStatus = BackendAccessStatus.READY,
    )

    @Test fun ownerRoleAloneDoesNotGrantModules() {
        val dashboard = requireNotNull(backendDashboard(session(setOf("VIEW_CATALOG")), true))
        assertEquals(listOf("catalog"), dashboard.modules.map { it.id })
        assertFalse(dashboard.canCreateSales)
    }

    @Test fun expiredOrSignedOutSessionDoesNotRenderAnActiveDashboard() {
        val current = session(setOf("VIEW_CATALOG"))
        assertNull(backendDashboard(current.copy(status = BackendAuthStatus.ANONYMOUS), true))
        assertNull(backendDashboard(current.copy(session = BackendSession("a".repeat(43), 1, 1)), true))
    }

    @Test fun inactiveBranchHidesSalesAndCashierEvenWithCapabilities() {
        val current = session(setOf("VIEW_CATALOG", "CREATE_SALES", "OPERATE_CASHIER"))
        val inactive = current.copy(context = current.context!!.copy(branch = current.context.branch!!.copy(isActive = false)))
        val dashboard = requireNotNull(backendDashboard(inactive, true))
        assertEquals(listOf("catalog"), dashboard.modules.map { it.id })
        assertFalse(dashboard.canCreateSales)
    }

    @Test fun webModulesRequireTheOfficialPortalAndCorrespondingCapabilities() {
        val current = session(setOf("MANAGE_PRODUCTS", "MANAGE_USERS", "VIEW_REPORTS"))
        assertTrue(requireNotNull(backendDashboard(current, false)).modules.isEmpty())
        assertEquals(setOf("products", "staff", "reports"), requireNotNull(backendDashboard(current, true)).modules.map { it.id }.toSet())
    }

    @Test fun mismatchedIdentityOrMissingRoleCannotBecomeAWorkingDashboard() {
        val current = session(setOf("VIEW_CATALOG", "CREATE_SALES"))
        assertNull(backendDashboard(current.copy(session = BackendSession("a".repeat(43), 2, Long.MAX_VALUE)), true))
        assertNull(backendDashboard(current.copy(context = current.context!!.copy(role = null)), true))
    }
}
