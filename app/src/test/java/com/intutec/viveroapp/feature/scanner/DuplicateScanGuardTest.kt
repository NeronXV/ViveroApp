package com.intutec.viveroapp.feature.scanner

import com.intutec.viveroapp.feature.scanner.camera.DuplicateScanGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateScanGuardTest {
    @Test
    fun `suppresses repeated code during cooldown`() {
        var now = 1_000L
        val guard = DuplicateScanGuard(cooldownMillis = 2_500L, now = { now })

        assertTrue(guard.shouldAccept("750100000001"))
        now += 1_000L
        assertFalse(guard.shouldAccept("750100000001"))
        now += 2_000L
        assertTrue(guard.shouldAccept("750100000001"))
    }

    @Test
    fun `reset accepts the same code immediately`() {
        val guard = DuplicateScanGuard(cooldownMillis = 10_000L, now = { 1_000L })

        assertTrue(guard.shouldAccept("PL-001"))
        guard.reset()
        assertTrue(guard.shouldAccept("PL-001"))
    }

    @Test
    fun `rejects blank values and accepts another code`() {
        val guard = DuplicateScanGuard(now = { 1_000L })

        assertFalse(guard.shouldAccept("   "))
        assertTrue(guard.shouldAccept("PL-001"))
        assertTrue(guard.shouldAccept("PL-014"))
    }
}
