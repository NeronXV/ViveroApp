package com.intutec.viveroapp.core.security

import com.intutec.viveroapp.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePermissionsTest {
    @Test
    fun `android matrix matches the shared role capabilities`() {
        val expected = mapOf(
            UserRole.SALES to setOf(
                AppPermission.VIEW_CATALOG,
                AppPermission.SCAN_PRODUCTS,
                AppPermission.CREATE_SALES,
                AppPermission.VIEW_OWN_SALES,
            ),
            UserRole.CASHIER to setOf(
                AppPermission.VIEW_CATALOG,
                AppPermission.OPERATE_CASHIER,
            ),
            UserRole.INVENTORY to setOf(
                AppPermission.VIEW_CATALOG,
                AppPermission.SCAN_PRODUCTS,
                AppPermission.MANAGE_PRODUCTS,
                AppPermission.MANAGE_INVENTORY,
                AppPermission.VIEW_INVENTORY_ALERTS,
            ),
            UserRole.MANAGER to setOf(
                AppPermission.VIEW_CATALOG,
                AppPermission.SCAN_PRODUCTS,
                AppPermission.CREATE_SALES,
                AppPermission.VIEW_OWN_SALES,
                AppPermission.OPERATE_CASHIER,
                AppPermission.VIEW_BRANCH_SALES,
                AppPermission.MANAGE_PRODUCTS,
                AppPermission.MANAGE_PRICES,
                AppPermission.MANAGE_DISCOUNTS,
                AppPermission.MANAGE_INVENTORY,
                AppPermission.VIEW_INVENTORY_ALERTS,
                AppPermission.VIEW_REPORTS,
            ),
            UserRole.ADMIN to AppPermission.entries.toSet(),
            UserRole.OWNER to AppPermission.entries.toSet(),
        )

        UserRole.entries.forEach { role ->
            assertEquals(expected.getValue(role), RolePermissions.permissionsFor(role))
        }
    }

    @Test
    fun `sales and cashier cannot access administrative capabilities`() {
        assertFalse(RolePermissions.can(UserRole.SALES, AppPermission.MANAGE_USERS))
        assertFalse(RolePermissions.can(UserRole.CASHIER, AppPermission.VIEW_ALL_SALES))
        assertFalse(RolePermissions.can(UserRole.CASHIER, AppPermission.ASSIGN_ROLES))
    }

    @Test
    fun `owner and admin expose all app capabilities`() {
        AppPermission.entries.forEach { permission ->
            assertTrue(RolePermissions.can(UserRole.OWNER, permission))
            assertTrue(RolePermissions.can(UserRole.ADMIN, permission))
        }
    }
}
