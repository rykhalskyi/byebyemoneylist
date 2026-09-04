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

    private fun parseStoresResponse(bodyStr: String): List<NextcloudStoreDto> {
        return try {
            val ocsWrapped = json.decodeFromString<OcsResponseWrapper<NextcloudStoresResponse>>(bodyStr)
            ocsWrapped.ocs.data.stores
        } catch (e: Exception) {
            val direct = json.decodeFromString<NextcloudStoresResponse>(bodyStr)
            direct.stores
        }
    }

    private fun parseStoreResponse(bodyStr: String): NextcloudStoreDto {
        return try {
            val ocsWrapped = json.decodeFromString<OcsResponseWrapper<NextcloudStoreResponse>>(bodyStr)
            ocsWrapped.ocs.data.store
        } catch (e: Exception) {
            val direct = json.decodeFromString<NextcloudStoreResponse>(bodyStr)
            direct.store
        } ?: throw Exception("Server returned an empty store payload.")
    }

    private fun parseProductsResponse(bodyStr: String): List<NextcloudProductDto> {
        return try {
            val ocsWrapped = json.decodeFromString<OcsResponseWrapper<NextcloudProductsResponse>>(bodyStr)
            ocsWrapped.ocs.data.products
        } catch (e: Exception) {
            val direct = json.decodeFromString<NextcloudProductsResponse>(bodyStr)
            direct.products
        }
    }

    private fun parseProductResponse(bodyStr: String): NextcloudProductDto {
        return try {
            val ocsWrapped = json.decodeFromString<OcsResponseWrapper<NextcloudProductResponse>>(bodyStr)
            ocsWrapped.ocs.data.product
        } catch (e: Exception) {
            val direct = json.decodeFromString<NextcloudProductResponse>(bodyStr)
            direct.product
        } ?: throw Exception("Server returned an empty product payload.")
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

    suspend fun fetchStores(
        serverUrl: String,
        username: String,
        pass: String
    ): Result<List<NextcloudStoreDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = sanitizeUrl(serverUrl)
            val credential = Credentials.basic(username, pass)

            var lastException: Exception? = null

            for (pathPrefix in getCandidatePaths()) {
                val requestUrl = "$cleanUrl$pathPrefix/api/stores?format=json"
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
                            return@runCatching parseStoresResponse(bodyStr)
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

            throw lastException ?: Exception("Could not fetch stores from Nextcloud.")
        }
    }

    suspend fun createStore(
        serverUrl: String,
        username: String,
        pass: String,
        name: String
    ): Result<NextcloudStoreDto> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = sanitizeUrl(serverUrl)
            val credential = Credentials.basic(username, pass)
            val payloadStr = json.encodeToString(NextcloudStoreCreateRequest(name))

            var lastException: Exception? = null

            for (pathPrefix in getCandidatePaths()) {
                val requestUrl = "$cleanUrl$pathPrefix/api/stores?format=json"
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
                            return@runCatching parseStoreResponse(bodyStr)
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

            throw lastException ?: Exception("Could not create store on Nextcloud.")
        }
    }

    suspend fun fetchProducts(
        serverUrl: String,
        username: String,
        pass: String,
        type: String = "all"
    ): Result<List<NextcloudProductDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = sanitizeUrl(serverUrl)
            val credential = Credentials.basic(username, pass)

            var lastException: Exception? = null

            for (pathPrefix in getCandidatePaths()) {
                val requestUrl = "$cleanUrl$pathPrefix/api/products?type=$type&format=json"
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
                            return@runCatching parseProductsResponse(bodyStr)
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

            throw lastException ?: Exception("Could not fetch products from Nextcloud.")
        }
    }

    suspend fun createProduct(
        serverUrl: String,
        username: String,
        pass: String,
        product: NextcloudProductCreateRequest
    ): Result<NextcloudProductDto> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = sanitizeUrl(serverUrl)
            val credential = Credentials.basic(username, pass)
            val payloadStr = json.encodeToString(product)

            var lastException: Exception? = null

            for (pathPrefix in getCandidatePaths()) {
                val requestUrl = "$cleanUrl$pathPrefix/api/products?format=json"
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
                            return@runCatching parseProductResponse(bodyStr)
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

            throw lastException ?: Exception("Could not create product on Nextcloud.")
        }
    }
}
