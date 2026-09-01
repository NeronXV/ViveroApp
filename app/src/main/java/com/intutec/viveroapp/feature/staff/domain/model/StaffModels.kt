package com.intutec.viveroapp.feature.staff.domain.model

import com.intutec.viveroapp.core.model.UserRole

data class StaffMember(
    val id: String,
    val fullName: String,
    val isActive: Boolean,
    val branch: StaffBranch?,
    val role: UserRole?,
)

data class StaffBranch(
    val id: String,
    val code: String,
    val name: String,
    val isActive: Boolean,
)

data class StaffDirectory(
    val members: List<StaffMember>,
    val branches: List<StaffBranch>,
)
