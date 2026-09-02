package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.feature.catalog.presentation.catalogImageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogImageUrlTest {
    @Test
    fun `builds public catalog image URL from a relative object key`() {
        assertEquals(
            "https://project.supabase.co/storage/v1/object/public/catalog-images/products/primary.jpg",
            catalogImageUrl("https://project.supabase.co/", "products/primary.jpg"),
        )
    }

    @Test
    fun `encodes each storage path segment without losing folders`() {
        assertEquals(
            "http://127.0.0.1:54321/storage/v1/object/public/catalog-images/productos/rosa%20blanca.jpg",
            catalogImageUrl("http://127.0.0.1:54321", "productos/rosa blanca.jpg"),
        )
    }

    @Test
    fun `rejects missing configuration and unsafe object keys`() {
        assertNull(catalogImageUrl("", "products/image.jpg"))
        assertNull(catalogImageUrl("https://project.supabase.co", "../image.jpg"))
        assertNull(catalogImageUrl("https://project.supabase.co", "/products/image.jpg"))
    }
}
