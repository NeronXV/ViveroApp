package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.feature.catalog.presentation.CatalogDiagnosticCause
import com.intutec.viveroapp.feature.catalog.presentation.toCatalogErrorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CatalogErrorMappingTest {
    @Test
    fun `technical timestamp error is not exposed to the user`() {
        val technicalMessage =
            "Text '2026-08-16T04:04:38.071382+00:00' could not be parsed at index 20"

        val state = IllegalStateException(technicalMessage).toCatalogErrorState()

        assertEquals("No pudimos cargar el catálogo. Intenta nuevamente", state.message)
        assertEquals(CatalogDiagnosticCause.INVALID_REMOTE_DATA, state.diagnosticCause)
        assertFalse(state.message.contains("parsed", ignoreCase = true))
        assertFalse(state.message.contains("2026-08-16"))
    }
}
