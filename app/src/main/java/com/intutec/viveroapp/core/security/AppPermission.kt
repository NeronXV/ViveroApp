package com.intutec.viveroapp.core.security

import com.intutec.viveroapp.core.model.UserRole

enum class AppPermission {
    VIEW_CATALOG,
    SCAN_PRODUCTS,
    CREATE_SALES,
    VIEW_OWN_SALES,
    OPERATE_CASHIER,
    VIEW_BRANCH_SALES,
    VIEW_ALL_SALES,
    MANAGE_PRODUCTS,
    MANAGE_PRICES,
    MANAGE_DISCOUNTS,
    MANAGE_INVENTORY,
    VIEW_INVENTORY_ALERTS,
    VIEW_REPORTS,
    MANAGE_BRANCHES,
    MANAGE_USERS,
    ASSIGN_ROLES,
    VIEW_AUDIT,
    MANAGE_SETTINGS,
}

object RolePermissions {
    private val permissions = mapOf(
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

    fun can(role: UserRole, permission: AppPermission): Boolean = permission in permissions.getValue(role)

    fun permissionsFor(role: UserRole): Set<AppPermission> = permissions.getValue(role)
}
