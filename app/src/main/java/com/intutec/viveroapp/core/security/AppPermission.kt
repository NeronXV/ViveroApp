package com.intutec.viveroapp.core.security

import com.intutec.viveroapp.core.model.UserRole

enum class AppPermission {
    VIEW_CATALOG,
    SCAN_PRODUCTS,
    MANAGE_CART,
    VIEW_OWN_TICKETS,
    MANAGE_CASHIER,
    MANAGE_INVENTORY,
    VIEW_REPORTS,
    MANAGE_PROMOTIONS,
    MANAGE_USERS,
    MANAGE_SETTINGS,
}

object RolePermissions {
    private val permissions = mapOf(
        UserRole.WORKER to setOf(
            AppPermission.VIEW_CATALOG,
            AppPermission.SCAN_PRODUCTS,
            AppPermission.MANAGE_CART,
            AppPermission.VIEW_OWN_TICKETS,
        ),
        UserRole.CASHIER to setOf(
            AppPermission.VIEW_CATALOG,
            AppPermission.MANAGE_CASHIER,
        ),
        UserRole.INVENTORY to setOf(
            AppPermission.VIEW_CATALOG,
            AppPermission.SCAN_PRODUCTS,
            AppPermission.MANAGE_INVENTORY,
        ),
        UserRole.MANAGER to setOf(
            AppPermission.VIEW_CATALOG,
            AppPermission.VIEW_REPORTS,
            AppPermission.MANAGE_INVENTORY,
            AppPermission.MANAGE_PROMOTIONS,
        ),
        UserRole.ADMIN to AppPermission.entries.toSet(),
        UserRole.OWNER to AppPermission.entries.toSet() - AppPermission.MANAGE_USERS - AppPermission.MANAGE_SETTINGS,
    )

    fun can(role: UserRole, permission: AppPermission): Boolean = permission in permissions.getValue(role)
}
