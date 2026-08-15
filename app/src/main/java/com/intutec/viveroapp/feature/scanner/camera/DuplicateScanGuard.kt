package com.intutec.viveroapp.feature.scanner.camera

class DuplicateScanGuard(
    private val cooldownMillis: Long = 2_500L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var lastCode: String? = null
    private var lastAcceptedAt = Long.MIN_VALUE

    @Synchronized
    fun shouldAccept(code: String): Boolean {
        val normalized = code.trim()
        if (normalized.isBlank()) return false
        val currentTime = now()
        if (normalized == lastCode && currentTime - lastAcceptedAt < cooldownMillis) return false
        lastCode = normalized
        lastAcceptedAt = currentTime
        return true
    }

    @Synchronized
    fun reset() {
        lastCode = null
        lastAcceptedAt = Long.MIN_VALUE
    }
}
