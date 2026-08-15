package com.intutec.viveroapp.feature.auth.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val branch: BranchDto? = null,
)

@Serializable
data class BranchDto(val name: String)

@Serializable
data class UserRoleDto(
    val role: RoleDto,
)

@Serializable
data class RoleDto(
    val name: String,
)
