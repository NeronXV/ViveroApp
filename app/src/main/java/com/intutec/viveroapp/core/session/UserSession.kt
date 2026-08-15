package com.intutec.viveroapp.core.session

import com.intutec.viveroapp.core.model.UserRole

data class UserSession(
    val userId: String,
    val email: String,
    val fullName: String,
    val role: UserRole,
    val branchName: String,
    val isDemo: Boolean,
)
