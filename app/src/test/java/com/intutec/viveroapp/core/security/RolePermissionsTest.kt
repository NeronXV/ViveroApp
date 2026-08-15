package com.intutec.viveroapp.core.security

import com.intutec.viveroapp.core.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePermissionsTest {
    @Test
    fun `worker cannot manage cashier or users`() {
        assertFalse(RolePermissions.can(UserRole.WORKER, AppPermission.MANAGE_CASHIER))
        assertFalse(RolePermissions.can(UserRole.WORKER, AppPermission.MANAGE_USERS))
    }

    @Test
    fun `admin has every permission`() {
        AppPermission.entries.forEach { permission ->
            assertTrue(RolePermissions.can(UserRole.ADMIN, permission))
        }
    }
}
