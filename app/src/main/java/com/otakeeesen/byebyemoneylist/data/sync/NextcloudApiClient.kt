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

    /**
     * List requests are full-state pushes (client wins): nullable scalars must
     * be sent explicitly as null so the server clears them, and booleans must
     * reflect the local value even when they equal the server default.
     */
    private val requestJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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

    private fun parseListsResponse(bodyStr: String): List<NextcloudListDto> {
        return try {
            val ocsWrapped = json.decodeFromString<OcsResponseWrapper<NextcloudListsResponse>>(bodyStr)
            ocsWrapped.ocs.data.lists
        } catch (e: Exception) {
            val direct = json.decodeFromString<NextcloudListsResponse>(bodyStr)
            direct.lists
        }
    }

    private fun parseListResponse(bodyStr: String): NextcloudListDto {
        return try {
            val ocsWrapped = json.decodeFromString<OcsResponseWrapper<NextcloudListResponse>>(bodyStr)
            ocsWrapped.ocs.data.list
        } catch (e: Exception) {
            val direct = json.decodeFromString<NextcloudListResponse>(bodyStr)
            direct.list
        } ?: throw Exception("Server returned an empty list payload.")
    }

    private fun parseListItemsResponse(bodyStr: String): List<NextcloudListItemDto> {
        return try {
            val ocsWrapped = json.decodeFromString<OcsResponseWrapper<NextcloudListItemsResponse>>(bodyStr)
            ocsWrapped.ocs.data.items
        } catch (e: Exception) {
            val direct = json.decodeFromString<NextcloudListItemsResponse>(bodyStr)
            direct.items
        }
    }

    private fun parseListItemResponse(bodyStr: String): NextcloudListItemDto {
        return try {
            val ocsWrapped = json.decodeFromString<OcsResponseWrapper<NextcloudListItemResponse>>(bodyStr)
            ocsWrapped.ocs.data.item
        } catch (e: Exception) {
            val direct = json.decodeFromString<NextcloudListItemResponse>(bodyStr)
            direct.item
        } ?: throw Exception("Server returned an empty list item payload.")
    }

    /**
     * Executes a request against every candidate app path until one succeeds
     * (caching the working path), returning the parsed payload. A 404 moves to
     * the next candidate path; when `acceptNotFound` is set a 404 on the final
     * attempt is treated as success (used for deletes where a missing remote
     * resource is already the desired end state).
     */
    private suspend fun <T> executeRequest(
        serverUrl: String,
        username: String,
        pass: String,
        method: String,
        apiPath: String,
        body: okhttp3.RequestBody?,
        acceptNotFound: Boolean = false,
        onResponse: (bodyStr: String) -> T
    ): T {
        val cleanUrl = sanitizeUrl(serverUrl)
        val credential = Credentials.basic(username, pass)
        val candidatePaths = getCandidatePaths()

        var lastException: Exception? = null
        for ((index, pathPrefix) in candidatePaths.withIndex()) {
            val requestUrl = "$cleanUrl$pathPrefix$apiPath"
            val isLastCandidate = index == candidatePaths.lastIndex

            val requestBuilder = Request.Builder()
                .url(requestUrl)
                .header("Authorization", credential)
                .header("OCS-APIRequest", "true")
                .header("Accept", "application/json")

            val request = when (method) {
                "GET" -> requestBuilder.get().build()
                "POST" -> requestBuilder.post(requireNotNull(body) { "POST requires a request body" }).build()
                "PUT" -> requestBuilder.put(requireNotNull(body) { "PUT requires a request body" }).build()
                "DELETE" -> requestBuilder.delete(body).build()
                else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
            }

            try {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        cachedWorkingPath = pathPrefix
                        return onResponse(bodyStr)
                    } else if (response.code == 404 && acceptNotFound) {
                        cachedWorkingPath = pathPrefix
                        lastException = Exception("HTTP 404 Not Found on $requestUrl")
                        if (isLastCandidate) {
                            return onResponse(bodyStr)
                        }
                    } else if (response.code == 404) {
                        lastException = Exception("HTTP 404 Not Found on $requestUrl")
                    } else {
                        throw Exception("HTTP ${response.code}: $bodyStr")
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: Exception("Could not reach Nextcloud ($method $apiPath).")
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

    suspend fun fetchLists(
        serverUrl: String,
        username: String,
        pass: String
    ): Result<List<NextcloudListDto>> = withContext(Dispatchers.IO) {
        runCatching {
            executeRequest(serverUrl, username, pass, "GET", "/api/lists?format=json", null) { body ->
                parseListsResponse(body)
            }
        }
    }

    suspend fun createList(
        serverUrl: String,
        username: String,
        pass: String,
        list: NextcloudListCreateRequest
    ): Result<NextcloudListDto> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = requestJson.encodeToString(NextcloudListCreateRequest.serializer(), list)
            executeRequest(
                serverUrl, username, pass, "POST", "/api/lists?format=json",
                payload.toRequestBody(jsonMediaType)
            ) { body -> parseListResponse(body) }
        }
    }

    suspend fun updateList(
        serverUrl: String,
        username: String,
        pass: String,
        listId: String,
        list: NextcloudListUpdateRequest
    ): Result<NextcloudListDto> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = requestJson.encodeToString(NextcloudListUpdateRequest.serializer(), list)
            executeRequest(
                serverUrl, username, pass, "PUT", "/api/lists/$listId?format=json",
                payload.toRequestBody(jsonMediaType)
            ) { body -> parseListResponse(body) }
        }
    }

    suspend fun deleteList(
        serverUrl: String,
        username: String,
        pass: String,
        listId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            executeRequest(
                serverUrl, username, pass, "DELETE", "/api/lists/$listId?format=json",
                null, acceptNotFound = true
            ) { }
        }
    }

    suspend fun fetchListItems(
        serverUrl: String,
        username: String,
        pass: String,
        listId: String
    ): Result<List<NextcloudListItemDto>> = withContext(Dispatchers.IO) {
        runCatching {
            executeRequest(
                serverUrl, username, pass, "GET", "/api/lists/$listId/items?format=json", null
            ) { body -> parseListItemsResponse(body) }
        }
    }

    suspend fun createListItem(
        serverUrl: String,
        username: String,
        pass: String,
        listId: String,
        item: NextcloudListItemCreateRequest
    ): Result<NextcloudListItemDto> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = requestJson.encodeToString(NextcloudListItemCreateRequest.serializer(), item)
            executeRequest(
                serverUrl, username, pass, "POST", "/api/lists/$listId/items?format=json",
                payload.toRequestBody(jsonMediaType)
            ) { body -> parseListItemResponse(body) }
        }
    }

    suspend fun updateListItem(
        serverUrl: String,
        username: String,
        pass: String,
        listId: String,
        itemId: String,
        item: NextcloudListItemUpdateRequest
    ): Result<NextcloudListItemDto> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = requestJson.encodeToString(NextcloudListItemUpdateRequest.serializer(), item)
            executeRequest(
                serverUrl, username, pass, "PUT", "/api/lists/$listId/items/$itemId?format=json",
                payload.toRequestBody(jsonMediaType)
            ) { body -> parseListItemResponse(body) }
        }
    }

    suspend fun deleteListItem(
        serverUrl: String,
        username: String,
        pass: String,
        listId: String,
        itemId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            executeRequest(
                serverUrl, username, pass, "DELETE", "/api/lists/$listId/items/$itemId?format=json",
                null, acceptNotFound = true
            ) { }
        }
    }
}
