package com.intutec.viveroapp.feature.auth.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("branch_id") val branchId: String? = null,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class BranchDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class UserRoleAssignmentDto(
    @SerialName("role_id") val roleId: Int,
    val role: RoleDto,
)

@Serializable
data class RoleDto(
    val name: String,
)

@Serializable
data class PermissionDto(
    @SerialName("permission_name") val permissionName: String,
)
