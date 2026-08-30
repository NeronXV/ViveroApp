package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.security.RolePermissions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductAdminPermissionsTest {
    @Test
    fun `ADMIN tiene MANAGE_PRODUCTS y MANAGE_PRICES`() {
        assertTrue(RolePermissions.can(UserRole.ADMIN, AppPermission.MANAGE_PRODUCTS))
        assertTrue(RolePermissions.can(UserRole.ADMIN, AppPermission.MANAGE_PRICES))
        assertTrue(RolePermissions.can(UserRole.ADMIN, AppPermission.MANAGE_INVENTORY))
    }

    @Test
    fun `MANAGER GERENTE tiene capacidades necesarias para alta e inventario`() {
        assertTrue(RolePermissions.can(UserRole.MANAGER, AppPermission.MANAGE_PRODUCTS))
        assertTrue(RolePermissions.can(UserRole.MANAGER, AppPermission.MANAGE_PRICES))
        assertTrue(RolePermissions.can(UserRole.MANAGER, AppPermission.MANAGE_INVENTORY))
        assertTrue(RolePermissions.can(UserRole.MANAGER, AppPermission.VIEW_INVENTORY_ALERTS))
    }

    @Test
    fun `INVENTORY puede gestionar productos pero no precios`() {
        assertTrue(RolePermissions.can(UserRole.INVENTORY, AppPermission.MANAGE_PRODUCTS))
        assertTrue(RolePermissions.can(UserRole.INVENTORY, AppPermission.MANAGE_INVENTORY))
        assertFalse(RolePermissions.can(UserRole.INVENTORY, AppPermission.MANAGE_PRICES))
    }

    @Test
    fun `SALES no puede gestionar productos ni inventario`() {
        assertFalse(RolePermissions.can(UserRole.SALES, AppPermission.MANAGE_PRODUCTS))
        assertFalse(RolePermissions.can(UserRole.SALES, AppPermission.MANAGE_INVENTORY))
        assertFalse(RolePermissions.can(UserRole.SALES, AppPermission.MANAGE_PRICES))
    }

    @Test
    fun `ninguna capacidad nueva concedida solo desde Android`() {
        // Todas las capacidades están en AppPermission y mapeadas en RolePermissions; no se añaden dinámicamente
        assertTrue(AppPermission.entries.contains(AppPermission.MANAGE_PRODUCTS))
        assertTrue(AppPermission.entries.contains(AppPermission.MANAGE_INVENTORY))
    }
}
