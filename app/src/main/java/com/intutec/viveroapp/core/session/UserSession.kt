package com.intutec.viveroapp.core.session

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission

enum class SessionMode { REMOTE, DEMO }

data class UserBranch(
    val id: String,
    val code: String,
    val name: String,
    val isActive: Boolean,
)

data class UserSession(
    val userId: String,
    val email: String,
    val fullName: String,
    val role: UserRole,
    val capabilities: Set<AppPermission>,
    val branch: UserBranch?,
    val mode: SessionMode,
) {
    val branchId: String? get() = branch?.id
    val branchName: String get() = branch?.name ?: "Sin sucursal"
    val isDemo: Boolean get() = mode == SessionMode.DEMO

    fun hasCapability(permission: AppPermission): Boolean = permission in capabilities

    fun canOperateAtBranch(permission: AppPermission): Boolean =
        hasCapability(permission) && branch?.isActive == true
}
