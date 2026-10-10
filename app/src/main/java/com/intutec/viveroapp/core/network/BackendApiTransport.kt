package com.intutec.viveroapp.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import java.net.URI
import javax.inject.Inject
import javax.inject.Named

internal fun backendApiOrigin(value: String, debug: Boolean): String {
    val uri = runCatching { URI(value) }.getOrNull()
    require(uri != null && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
        && uri.path in listOf("", "/") && !value.any { it.isWhitespace() }) { "Configura la URL pública del Backend API." }
    require(uri.scheme == "https" || (debug && uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))) {
        "Backend API requiere HTTPS; HTTP local solo se admite en debug."
    }
    return value.trimEnd('/')
}

data class BackendApiResponse(val status: Int, val body: String)
interface BackendApiTransport {
    suspend fun login(body: String): BackendApiResponse = throw UnsupportedOperationException()
    suspend fun recovery(body: String): BackendApiResponse = throw UnsupportedOperationException()
    suspend fun get(path: String, token: String): BackendApiResponse = throw UnsupportedOperationException()
    suspend fun get(path: String, token: String, query: Map<String, String>): BackendApiResponse =
        if (query.isEmpty()) get(path, token) else throw UnsupportedOperationException()
    suspend fun post(path: String, token: String, headers: Map<String, String>, body: String): BackendApiResponse
}
class KtorBackendApiTransport @Inject constructor(
    @param:Named("backendApi") private val client: HttpClient,
    @param:Named("backendApiOrigin") private val origin: String,
) : BackendApiTransport {
    override suspend fun login(body: String): BackendApiResponse = execute("/api/v1/auth/login", HttpMethod.Post, null, emptyMap(), body)
    override suspend fun recovery(body: String): BackendApiResponse = execute("/api/v1/auth/recovery", HttpMethod.Post, null, emptyMap(), body)
    override suspend fun get(path: String, token: String): BackendApiResponse = execute(path, HttpMethod.Get, token, emptyMap(), null)
    override suspend fun get(path: String, token: String, query: Map<String, String>): BackendApiResponse =
        execute(path, HttpMethod.Get, token, emptyMap(), null, query)
    override suspend fun post(path: String, token: String, headers: Map<String, String>, body: String): BackendApiResponse =
        execute(path, HttpMethod.Post, token, headers, body)
    private suspend fun execute(path: String, method: HttpMethod, token: String?, headers: Map<String, String>, body: String?, query: Map<String, String> = emptyMap()): BackendApiResponse {
        require(Regex("^/api/v1/[a-z0-9/-]+$").matches(path) && !path.contains("//"))
        require(if (token == null) path in setOf("/api/v1/auth/login", "/api/v1/auth/recovery") && method == HttpMethod.Post else Regex("^[A-Za-z0-9_-]{43}$").matches(token))
        val response = client.request(origin + path) {
            url { query.forEach { (name, value) -> parameters.append(name, value) } }
            this.method = method
            if (token != null) header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            header("X-Vivero-Folio-Format", "short-v1")
            headers.forEach { (name, value) -> header(name, value) }
            header("Cache-Control", "no-store")
            if (body != null) setBody(body)
        }
        val text = response.bodyAsText()
        check(text.length <= 65536) { "Respuesta del Backend API demasiado grande." }
        return BackendApiResponse(response.status.value, text)
    }
}
