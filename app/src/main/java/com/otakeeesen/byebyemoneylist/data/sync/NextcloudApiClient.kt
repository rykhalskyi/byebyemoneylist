package com.otakeeesen.byebyemoneylist.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class NextcloudApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private var cachedWorkingPath: String? = null

    private fun sanitizeUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            val isLocalOrHttpPort = clean.contains("localhost") || clean.contains("127.0.0.1") ||
                    clean.contains("10.0.2.2") || clean.contains("192.168.") ||
                    clean.contains(":80") || clean.contains(":8080") || clean.contains(":8000")
            clean = if (isLocalOrHttpPort) "http://$clean" else "https://$clean"
        }
        return clean.trimEnd('/')
    }

    private fun getCandidatePaths(): List<String> {
        val cached = cachedWorkingPath
        val defaultPaths = listOf(
            "/ocs/v2.php/apps/byebyemoneylist"/*,
            "/index.php/apps/byebyemoneylist",
            "/apps/byebyemoneylist"*/
        )
        return if (cached != null) {
            listOf(cached) + defaultPaths.filter { it != cached }
        } else {
            defaultPaths
        }
    }

    private fun parseCategoriesResponse(bodyStr: String): List<NextcloudCategoryDto> {
        return try {
            val ocsWrapped = json.decodeFromString<OcsResponseWrapper<NextcloudCategoriesResponse>>(bodyStr)
            ocsWrapped.ocs.data.categories
        } catch (e: Exception) {
            val direct = json.decodeFromString<NextcloudCategoriesResponse>(bodyStr)
            direct.categories
        }
    }

    suspend fun testConnection(serverUrl: String, username: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = sanitizeUrl(serverUrl)
            val credential = Credentials.basic(username, pass)

            var lastException: Exception? = null

            for (pathPrefix in getCandidatePaths()) {
                val requestUrl = "$cleanUrl$pathPrefix/api/categories?format=json"
                val request = Request.Builder()
                    .url(requestUrl)
                    .header("Authorization", credential)
                    .header("OCS-APIRequest", "true")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            cachedWorkingPath = pathPrefix
                            return@runCatching true
                        } else if (response.code != 404) {
                            throw Exception("Server returned HTTP ${response.code}: ${response.message}")
                        } else {
                            lastException = Exception("HTTP 404 Not Found on $requestUrl")
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }

            throw lastException ?: Exception("Could not connect to Nextcloud ByeByeMoneyList app. Check server URL and ensure app is enabled.")
        }
    }

    suspend fun fetchCategories(serverUrl: String, username: String, pass: String): Result<List<NextcloudCategoryDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = sanitizeUrl(serverUrl)
            val credential = Credentials.basic(username, pass)

            var lastException: Exception? = null

            for (pathPrefix in getCandidatePaths()) {
                val requestUrl = "$cleanUrl$pathPrefix/api/categories?format=json"
                val request = Request.Builder()
                    .url(requestUrl)
                    .header("Authorization", credential)
                    .header("OCS-APIRequest", "true")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            cachedWorkingPath = pathPrefix
                            return@runCatching parseCategoriesResponse(bodyStr)
                        } else if (response.code != 404) {
                            throw Exception("HTTP ${response.code}: $bodyStr")
                        } else {
                            lastException = Exception("HTTP 404 Not Found on $requestUrl")
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }

            throw lastException ?: Exception("Could not fetch categories from Nextcloud.")
        }
    }

    suspend fun createCategoryBatch(
        serverUrl: String,
        username: String,
        pass: String,
        categories: List<NextcloudCategoryDto>
    ): Result<List<NextcloudCategoryDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = sanitizeUrl(serverUrl)
            val credential = Credentials.basic(username, pass)
            val payloadStr = json.encodeToString(NextcloudBatchCategoriesRequest(categories))

            var lastException: Exception? = null

            for (pathPrefix in getCandidatePaths()) {
                val requestUrl = "$cleanUrl$pathPrefix/api/categories/batch?format=json"
                val requestBody = payloadStr.toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(requestUrl)
                    .header("Authorization", credential)
                    .header("OCS-APIRequest", "true")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            cachedWorkingPath = pathPrefix
                            return@runCatching parseCategoriesResponse(bodyStr)
                        } else if (response.code != 404) {
                            Log.e("Batch","HTTP ${response.code}: $bodyStr")
                            throw Exception("HTTP ${response.code}: $bodyStr")
                        } else {
                            lastException = Exception("HTTP 404 Not Found on $requestUrl")
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }

            throw lastException ?: Exception("Could not create category batch on Nextcloud.")
        }
    }
}
