package com.intutec.viveroapp.feature.staff.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RemoteStaffPageDto(
    val schemaVersion: Int,
    val items: List<RemoteStaffMemberDto>,
    val page: RemoteStaffPageInfoDto,
)

@Serializable
data class RemoteStaffPageInfoDto(
    val hasMore: Boolean,
    val nextCursor: RemoteStaffCursorDto? = null,
)

@Serializable
data class RemoteStaffCursorDto(val fullName: String, val id: String)

@Serializable
data class RemoteStaffMemberDto(
    val id: String,
    val fullName: String,
    val isActive: Boolean,
    val branch: RemoteStaffBranchDto? = null,
    val role: RemoteStaffRoleDto? = null,
)

@Serializable
data class RemoteStaffRoleDto(val name: String, val displayName: String)

@Serializable
data class RemoteStaffBranchDto(
    val id: String,
    val code: String,
    val name: String,
    val isActive: Boolean,
)

@Serializable
data class RemoteBranchPageDto(
    val schemaVersion: Int,
    val items: List<RemoteAdminBranchDto>,
    val page: RemoteBranchPageInfoDto,
)

@Serializable
data class RemoteBranchPageInfoDto(
    val hasMore: Boolean,
    val nextCursor: RemoteBranchCursorDto? = null,
)

@Serializable
data class RemoteBranchCursorDto(val code: String, val id: String)

@Serializable
data class RemoteAdminBranchDto(
    val id: String,
    val code: String,
    val name: String,
    val isActive: Boolean,
)
