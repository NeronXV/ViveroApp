package com.intutec.viveroapp.feature.home

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.RolePermissions
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.home.data.repository.SessionDashboardRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStaffModuleTest {
    @Test
    fun `admin y owner ven Personal pero gerente no`() = runTest {
        assertTrue(dashboard(UserRole.ADMIN).modules.any { it.id == "staff" })
        assertTrue(dashboard(UserRole.OWNER).modules.any { it.id == "staff" })
        assertFalse(dashboard(UserRole.MANAGER).modules.any { it.id == "staff" })
    }

    @Test
    fun `cada familia de rol recibe el titulo de panel correcto`() = runTest {
        assertEquals("Panel de trabajador", dashboard(UserRole.SALES).workspaceTitle)
        assertEquals("Panel de gerencia", dashboard(UserRole.MANAGER).workspaceTitle)
        assertEquals("Panel de administración", dashboard(UserRole.OWNER).workspaceTitle)
    }

    private suspend fun dashboard(role: UserRole) = SessionDashboardRepository(
        SessionStore().apply {
            update(UserSession(
                userId = "11111111-1111-4111-8111-111111111111",
                email = "test@example.test",
                fullName = "Prueba",
                role = role,
                capabilities = RolePermissions.permissionsFor(role),
                branch = UserBranch("22222222-2222-4222-8222-222222222222", "CENTRO", "Centro", true),
                mode = SessionMode.REMOTE,
            ))
        },
    ).getDashboard().getOrThrow()
}
