package com.intutec.viveroapp.feature.staff.domain.repository

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.feature.staff.domain.model.StaffDirectory

interface StaffRepository {
    suspend fun getDirectory(): Result<StaffDirectory>
    suspend fun assignRole(userId: String, role: UserRole): Result<Unit>
    suspend fun assignBranch(userId: String, branchId: String): Result<Unit>
    suspend fun setActive(userId: String, active: Boolean): Result<Unit>
}
