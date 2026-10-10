package com.intutec.viveroapp.core.network

import com.intutec.viveroapp.core.di.BackendNetworkModule
import com.intutec.viveroapp.feature.cart.data.remote.BackendSaleRemoteDataSource
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleAttempt
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleLine
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleException
import com.intutec.viveroapp.feature.cart.domain.repository.isBackendSaleFolio
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendFolioNegotiationTest {
    @Test fun readTransportNegotiatesFoliosWhileNativeSalesStayBlocked() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var responseFolio = "VD-0009"
        val formats = mutableListOf<String>()
        server.createContext("/api/v1/sales") { request ->
            formats.add(request.requestHeaders.getFirst("X-Vivero-Folio-Format"))
            request.requestBody.readBytes()
            val bytes = """{"schema_version":1,"id":9,"folio":"$responseFolio","branch_id":3,"created_by":2,"status":"SENT_TO_CASHIER","subtotal_cents":900,"discount_cents":0,"total_cents":900,"created_at":"2026-10-01T12:00:00Z","idempotent_replay":false}""".toByteArray()
            request.responseHeaders.add("Content-Type", "application/json")
            request.sendResponseHeaders(201, bytes.size.toLong())
            request.responseBody.use { it.write(bytes) }
        }
        server.start()
        val client = BackendNetworkModule.client()
        try {
            val transport = KtorBackendApiTransport(client, "http://127.0.0.1:${server.address.port}")
            val source = BackendSaleRemoteDataSource(transport)
            val attempt = BackendSaleAttempt.restore(BackendSaleIdentity(2, 3), "b".repeat(64), listOf(BackendSaleLine(4, 2)), 900)
            assertThrows(BackendSaleException::class.java) { runBlocking { source.submit("a".repeat(43), attempt) } }
            assertThrows(BackendSaleException::class.java) { runBlocking { source.recover("a".repeat(43), attempt) } }
            assertTrue(formats.isEmpty())
            val short = Json.parseToJsonElement(transport.get("/api/v1/sales", "a".repeat(43)).body).jsonObject["folio"]!!.jsonPrimitive.content
            assertEquals("VD-0009", short); assertTrue(isBackendSaleFolio(short))
            responseFolio = "VD-" + "A".repeat(24)
            val legacy = Json.parseToJsonElement(transport.get("/api/v1/sales", "a".repeat(43)).body).jsonObject["folio"]!!.jsonPrimitive.content
            assertEquals(responseFolio, legacy); assertTrue(isBackendSaleFolio(legacy))
            assertEquals(listOf("short-v1", "short-v1"), formats)
        } finally { client.close(); server.stop(0) }
    }
}
