package com.intutec.viveroapp.feature.home

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.RolePermissions
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.home.data.repository.SessionDashboardRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardProductsModuleTest {
    private fun repoWith(role: UserRole) = SessionDashboardRepository(SessionStore().apply {
        update(UserSession("u1", "a@b.com", "Test", role, RolePermissions.permissionsFor(role), UserBranch("b1", "CENTRO", "Centro", true), SessionMode.REMOTE))
    })

    @Test
    fun `ADMIN ve modulo Productos`() = runTest {
        val dash = repoWith(UserRole.ADMIN).getDashboard().getOrNull()!!
        assertTrue(dash.modules.any { it.id == "products" })
    }

    @Test
    fun `MANAGER ve modulo Productos e Inventario`() = runTest {
        val dash = repoWith(UserRole.MANAGER).getDashboard().getOrNull()!!
        assertTrue(dash.modules.any { it.id == "products" })
        assertTrue(dash.modules.any { it.id == "inventory" })
    }

    @Test
    fun `SALES no ve Productos ni Inventario`() = runTest {
        val dash = repoWith(UserRole.SALES).getDashboard().getOrNull()!!
        assertFalse(dash.modules.any { it.id == "products" })
        assertFalse(dash.modules.any { it.id == "inventory" })
    }
}
