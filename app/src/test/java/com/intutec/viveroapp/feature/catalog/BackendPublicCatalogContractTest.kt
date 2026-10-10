package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.core.network.BackendApiResponse
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.feature.catalog.data.remote.BackendCatalogRemoteDataSource
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BackendPublicCatalogContractTest {
    @Test fun currentPublicSnapshotIsAcceptedWithoutNetworkOrAuthentication() = runTest {
        val directory=System.getenv("VIVERO_PILOT_PUBLIC_CONTRACT_DIR")
        assumeTrue("Optional read-only public snapshot", !directory.isNullOrBlank())
        val root=File(requireNotNull(directory))
        val transport=object:BackendApiTransport {
            override suspend fun get(path:String,token:String,query:Map<String,String>) = BackendApiResponse(200,
                File(root,if(path=="/api/v1/products") "catalog-public.json" else "categories-public.json").readText())
            override suspend fun post(path:String,token:String,headers:Map<String,String>,body:String):BackendApiResponse = error("No operations permitted")
        }
        val source=BackendCatalogRemoteDataSource(transport)
        val products=source.products("a".repeat(43),100)
        val categories=source.categories("a".repeat(43),100)
        assertEquals(Json.parseToJsonElement(File(root,"catalog-public.json").readText()).jsonObject["items"]!!.jsonArray.size,products.items.size)
        assertFalse(categories.items.isEmpty())
        assertTrue(products.items.all { p -> categories.items.any { it.id==p.categoryId } })
    }
}
