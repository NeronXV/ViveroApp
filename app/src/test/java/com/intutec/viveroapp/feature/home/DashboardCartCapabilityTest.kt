package com.intutec.viveroapp.feature.home

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.home.data.repository.SessionDashboardRepository
import com.intutec.viveroapp.feature.home.domain.model.DashboardModule
import com.intutec.viveroapp.feature.home.presentation.onHomeModuleClick
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCartCapabilityTest {
    @Test
    fun `OWNER with CREATE_SALES sees enabled cart`() = runTest {
        val module = dashboardFor(UserRole.OWNER, setOf(AppPermission.CREATE_SALES)).cartModule()

        assertTrue(module.enabled)
    }

    @Test
    fun `SALES with CREATE_SALES sees enabled cart`() = runTest {
        val module = dashboardFor(UserRole.SALES, setOf(AppPermission.CREATE_SALES)).cartModule()

        assertTrue(module.enabled)
    }

    @Test
    fun `user without CREATE_SALES does not see cart`() = runTest {
        val dashboard = dashboardFor(UserRole.OWNER, setOf(AppPermission.VIEW_CATALOG))

        assertFalse(dashboard.modules.any { it.id == "cart" })
    }

    @Test
    fun `cart module navigation invokes existing cart route callback`() {
        var catalogOpens = 0
        var cartOpens = 0
        val module = DashboardModule("cart", "Carrito actual", "Prepara una nueva venta", true)

        onHomeModuleClick(
            module,
            onCatalogClick = { catalogOpens += 1 },
            onCartClick = { cartOpens += 1 },
            onCashierClick = {},
            onInventoryClick = {},
            onReportsClick = {},
            onMySalesClick = {},
            onProductsClick = {},
        )

        assertEquals(0, catalogOpens)
        assertEquals(1, cartOpens)
    }

    @Test
    fun `cashier module depends on OPERATE_CASHIER and active branch`() = runTest {
        val allowed = dashboardFor(UserRole.CASHIER, setOf(AppPermission.OPERATE_CASHIER))
        val denied = dashboardFor(UserRole.CASHIER, setOf(AppPermission.VIEW_CATALOG))

        assertTrue(allowed.modules.any { it.id == "cashier" })
        assertFalse(denied.modules.any { it.id == "cashier" })
    }

    @Test
    fun `cashier module invokes cashier navigation callback`() {
        var cashierOpens = 0
        onHomeModuleClick(
            DashboardModule("cashier", "Caja", "Comandas", true),
            onCatalogClick = {},
            onCartClick = {},
            onCashierClick = { cashierOpens += 1 },
            onInventoryClick = {},
            onReportsClick = {},
            onMySalesClick = {},
            onProductsClick = {},
        )

        assertEquals(1, cashierOpens)
    }

    @Test
    fun `inventory module depends on MANAGE_INVENTORY and invokes its callback`() = runTest {
        val allowed = dashboardFor(UserRole.MANAGER, setOf(AppPermission.MANAGE_INVENTORY))
        val denied = dashboardFor(UserRole.MANAGER, setOf(AppPermission.VIEW_CATALOG))
        var inventoryOpens = 0

        assertTrue(allowed.modules.any { it.id == "inventory" })
        assertFalse(denied.modules.any { it.id == "inventory" })
        onHomeModuleClick(
            allowed.modules.single { it.id == "inventory" },
            onCatalogClick = {},
            onCartClick = {},
            onCashierClick = {},
            onInventoryClick = { inventoryOpens += 1 },
            onReportsClick = {},
            onMySalesClick = {},
            onProductsClick = {},
        )
        assertEquals(1, inventoryOpens)
    }

    private suspend fun dashboardFor(role: UserRole, capabilities: Set<AppPermission>) =
        SessionDashboardRepository(
            SessionStore().apply {
                update(
                    UserSession(
                        userId = "11111111-1111-4111-8111-111111111111",
                        email = "user@example.test",
                        fullName = "User",
                        role = role,
                        capabilities = capabilities,
                        branch = UserBranch(
                            id = "22222222-2222-4222-8222-222222222222",
                            code = "CENTRO",
                            name = "Sucursal Centro",
                            isActive = true,
                        ),
                        mode = SessionMode.REMOTE,
                    ),
                )
            },
        ).getDashboard().getOrThrow()

    private fun com.intutec.viveroapp.feature.home.domain.model.Dashboard.cartModule() =
        modules.single { it.id == "cart" }
}
