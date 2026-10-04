package com.intutec.viveroapp.core.session

fun BackendSessionState.authorizedSession(capability: String, branchRequired: Boolean = false, now: Long = System.currentTimeMillis()): BackendSession? {
    val active = session ?: return null
    val access = context ?: return null
    return active.takeIf {
        status == BackendAuthStatus.AUTHENTICATED && accessStatus == BackendAccessStatus.READY &&
            it.expiresAtMillis > now && access.user.id == it.userId && access.accessState == "ACTIVE" &&
            capability in access.capabilities && (!branchRequired || access.branch?.isActive == true)
    }
}
