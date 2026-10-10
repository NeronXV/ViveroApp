package com.intutec.viveroapp.feature.cart.domain.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendSaleFolioTest {
    @Test fun legacyAndPersistedAliasesRemainReadable() {
        listOf("VD-0001", "VD-9999", "VD-10000", "VD-" + "A".repeat(24)).forEach { assertTrue(isBackendSaleFolio(it)) }
        listOf("VD-1", "VD-0000", "VD-00001", "VD-9007199254740992", "VW-0001", "VD-ABCD").forEach { assertFalse(isBackendSaleFolio(it)) }
    }
}
