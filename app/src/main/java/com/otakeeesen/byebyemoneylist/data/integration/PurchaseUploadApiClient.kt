package com.otakeeesen.byebyemoneylist.data.integration

import com.otakeeesen.byebyemoneylist.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal one-way OCS client for the purchase import inbox.
 *
 * Stateless by design: credentials are passed per call so the client is easy to
 * test and the repository owns the preference lookup.
 */
class PurchaseUploadApiClient(
    private val client: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var cachedPath: String? = null

    suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = execute(
                serverUrl = serverUrl,
                username = username,
                password = password,
                method = "GET",
                apiPath = "/api/categories?format=json",
                body = null,
            )
            when {
                response.code in 200..299 -> Result.success(Unit)
                response.code == 401 || response.code == 403 -> throw PurchaseUploadAuthException()
                else -> throw PurchaseUploadRejectedException(errorMessage(response.body))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upload(
        serverUrl: String,
        username: String,
        password: String,
        payload: PurchaseUploadRequest,
    ): Result<PurchaseUploadApiResult> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(PurchaseUploadRequest.serializer(), payload)
                .toRequestBody(jsonMediaType)
            val response = execute(
                serverUrl = serverUrl,
                username = username,
                password = password,
                method = "POST",
                apiPath = "/api/purchase-imports",
                body = body,
            )
            when {
                response.code in 200..299 -> Result.success(parseUploadResponse(response.body))
                response.code == 409 -> Result.success(
                    PurchaseUploadApiResult(importId = null, alreadyImported = true)
                )
                response.code == 401 || response.code == 403 -> throw PurchaseUploadAuthException()
                response.code == 422 -> throw PurchaseUploadRejectedException(errorMessage(response.body))
                else -> throw PurchaseUploadRejectedException(errorMessage(response.body))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun execute(
        serverUrl: String,
        username: String,
        password: String,
        method: String,
        apiPath: String,
        body: RequestBody?,
    ): HttpResult {
        val baseUrl = sanitizeUrl(serverUrl)
        if (!BuildConfig.DEBUG && !baseUrl.startsWith("https://")) {
            throw PurchaseUploadRejectedException("An HTTPS server URL is required")
        }
        val credential = Credentials.basic(username, password)
        val paths = buildPaths()
        var lastNetworkError: IOException? = null

        for ((index, prefix) in paths.withIndex()) {
            val request = Request.Builder()
                .url("$baseUrl$prefix$apiPath")
                .header("Authorization", credential)
                .header("OCS-APIRequest", "true")
                .header("Accept", "application/json")
                .apply {
                    when (method) {
                        "GET" -> get()
                        "POST" -> post(requireNotNull(body) { "POST requires a body" })
                        else -> throw IllegalArgumentException("Unsupported method: $method")
                    }
                }
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.code == 404) {
                        if (index < paths.lastIndex) {
                            return@use
                        }
                        throw PurchaseUploadAppNotFoundException()
                    }
                    if (response.isSuccessful) {
                        cachedPath = prefix
                    }
                    return HttpResult(response.code, responseBody)
                }
            } catch (e: IOException) {
                lastNetworkError = e
            }
        }

        throw PurchaseUploadNetworkException(lastNetworkError)
    }

    private fun buildPaths(): List<String> {
        val default = listOf(DEFAULT_PATH)
        val cached = cachedPath ?: return default
        return listOf(cached) + default.filter { it != cached }
    }

    private fun sanitizeUrl(url: String): String {
        val clean = url.trim()
        val withScheme = if (clean.startsWith("http://") || clean.startsWith("https://")) {
            clean
        } else {
            "https://$clean"
        }
        return withScheme.trimEnd('/')
    }

    private fun parseUploadResponse(body: String): PurchaseUploadApiResult {
        val element = json.parseToJsonElement(body)
        val data = element.jsonObject["ocs"]?.jsonObject?.get("data") ?: element
        val response = json.decodeFromJsonElement(
            PurchaseImportUploadResponse.serializer(),
            data,
        )
        return PurchaseUploadApiResult(
            importId = response.import?.id,
            alreadyImported = response.duplicate,
        )
    }

    private fun errorMessage(body: String): String {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["ocs"]?.jsonObject?.get("data")?.jsonObject
            data?.get("message")?.jsonPrimitive?.content
                ?: root["message"]?.jsonPrimitive?.content
        }.getOrNull() ?: "Server rejected the import"
    }

    private data class HttpResult(val code: Int, val body: String)

    companion object {
        private const val DEFAULT_PATH = "/ocs/v2.php/apps/byebyemoneylist"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
