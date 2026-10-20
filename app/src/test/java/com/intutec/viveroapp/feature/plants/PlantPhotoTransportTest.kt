package com.intutec.viveroapp.feature.plants

import com.intutec.viveroapp.core.di.BackendNetworkModule
import com.intutec.viveroapp.core.network.KtorBackendApiTransport
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlantPhotoTransportTest {
    @Test fun draftPhotoUsesScopedBinaryEndpointWithoutProductQuery() = runBlocking {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val photo=byteArrayOf(0,1,-1,2)
        var observed:List<String?> = emptyList()
        var status=200
        server.createContext("/api/v1/cataloging/7/photo") { request ->
            observed=listOf(request.requestMethod,request.requestURI.query,
                request.requestHeaders.getFirst("Authorization"),
                request.requestHeaders.getFirst("X-Expected-Actor-Id"),
                request.requestHeaders.getFirst("X-Expected-Branch-Id"))
            request.responseHeaders.add("Content-Type","image/webp")
            request.sendResponseHeaders(status,photo.size.toLong())
            request.responseBody.use { it.write(photo) }
        }
        server.start()
        val client=BackendNetworkModule.client()
        try {
            val transport=KtorBackendApiTransport(client,"http://127.0.0.1:${server.address.port}")
            val headers=mapOf("X-Expected-Actor-Id" to "2","X-Expected-Branch-Id" to "3")
            assertArrayEquals(photo,transport.plantDraftImage(7,"a".repeat(43),headers))
            assertEquals(listOf("GET",null,"Bearer ${"a".repeat(43)}","2","3"),observed)
            status=404
            assertNull(transport.plantDraftImage(7,"a".repeat(43),headers))
        } finally { client.close();server.stop(0) }
    }
}
