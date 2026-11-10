package com.otakeeesen.byebyemoneylist.data.integration

import com.otakeeesen.byebyemoneylist.data.ListKind
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PurchaseUploadRepositoryTest {

    private val dao = mock<ShoppingListDao>()
    private val storeDao = mock<StoreDao>()
    private val categoryDao = mock<CategoryDao>()
    private val productDao = mock<ProductDao>()
    private val db = mock<AppDatabase>()
    private val preferences = mock<PreferencesManager>()

    private val json = Json { ignoreUnknownKeys = true }

    private var httpCalls = 0
    private var nextCode = 200
    private var nextBody = """{"import":{"id":"imp-1"},"duplicate":false}"""
    private var nextException: Throwable? = null
    private var lastRequestBody: String? = null

    private val eligibleList = ShoppingListEntity(
        id = 1L,
        name = "Weekly",
        createDate = 1000L,
        purchaseDate = 2000L,
        storeId = null,
        isFinished = true,
        kind = ListKind.PURCHASE.name,
    )

    private fun repository(): PurchaseUploadRepository {
        whenever(db.shoppingListDao()).thenReturn(dao)
        whenever(db.storeDao()).thenReturn(storeDao)
        whenever(db.categoryDao()).thenReturn(categoryDao)
        whenever(db.productDao()).thenReturn(productDao)
        whenever(preferences.isNextcloudConfigured()).thenReturn(true)
        whenever(preferences.getNextcloudUrl()).thenReturn("https://cloud.example.com")
        whenever(preferences.getNextcloudUsername()).thenReturn("user")
        whenever(preferences.getNextcloudPassword()).thenReturn("pass")
        whenever(preferences.getSyncUserId()).thenReturn("client")
        whenever(preferences.getCurrencySymbol()).thenReturn("€")
        whenever(dao.getCategoriesForShoppingListSync(any())).thenReturn(emptyList())
        whenever(dao.getItemsWithProductForListsSync(any())).thenReturn(emptyList())
        whenever(categoryDao.getAllCategoriesOnce()).thenReturn(emptyList())
        whenever(productDao.getProductsByIds(any())).thenReturn(emptyList())

        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                httpCalls++
                val request = chain.request()
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                lastRequestBody = buffer.readUtf8()
                nextException?.let { throw it }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(nextCode)
                    .message("test")
                    .body(nextBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        return PurchaseUploadRepository(db, preferences, PurchaseUploadApiClient(httpClient))
    }

    private fun stubList(list: ShoppingListEntity = eligibleList, uploadId: String? = "upload-1") {
        whenever(dao.getShoppingListById(list.id)).thenReturn(list)
        whenever(dao.getUploadId(list.id)).thenReturn(uploadId)
    }

    private fun uploadedPayload(): PurchaseUploadRequest =
        json.decodeFromString<PurchaseUploadRequest>(lastRequestBody ?: error("No upload body captured"))

    @Test
    fun `uploads an eligible list and records the upload timestamp`() = runBlocking {
        val repository = repository()
        stubList()

        val result = repository.uploadList(1L)

        assertTrue(result is PurchaseUploadResult.Success)
        verify(dao).updateLastUploadedAt(eq(1L), any())
        assertEquals("Weekly", uploadedPayload().list.name)
    }

    @Test
    fun `reuses a stable upload id on resend`() = runBlocking {
        val repository = repository()
        stubList(uploadId = "stable-id")

        repository.uploadList(1L)

        assertEquals("stable-id", uploadedPayload().listId)
        verify(dao, never()).setUploadId(any(), any())
    }

    @Test
    fun `generates and persists an upload id when missing`() = runBlocking {
        val repository = repository()
        stubList(uploadId = null)

        repository.uploadList(1L)

        val idCaptor = argumentCaptor<String>()
        verify(dao).setUploadId(eq(1L), idCaptor.capture())
        assertEquals(idCaptor.firstValue, uploadedPayload().listId)
    }

    @Test
    fun `returns NotConfigured without touching the network`() = runBlocking {
        val repository = repository()
        whenever(preferences.isNextcloudConfigured()).thenReturn(false)

        assertEquals(PurchaseUploadResult.NotConfigured, repository.uploadList(1L))
        assertEquals(0, httpCalls)
    }

    @Test
    fun `rejects ineligible lists`() = runBlocking {
        val repository = repository()
        val ineligible = listOf(
            eligibleList.copy(isFinished = false),
            eligibleList.copy(kind = ListKind.NEED_TO_BUY.name),
            eligibleList.copy(isIncome = true),
            eligibleList.copy(isSubscription = true),
        )

        ineligible.forEach { list ->
            stubList(list)
            assertEquals(PurchaseUploadResult.NotEligible, repository.uploadList(list.id))
        }
        assertEquals(0, httpCalls)
    }

    @Test
    fun `maps auth errors`() = runBlocking {
        val repository = repository()
        stubList()
        nextCode = 401

        assertEquals(PurchaseUploadResult.AuthError, repository.uploadList(1L))
        verify(dao, never()).updateLastUploadedAt(any(), any())
    }

    @Test
    fun `maps app-not-found`() = runBlocking {
        val repository = repository()
        stubList()
        nextCode = 404

        assertEquals(PurchaseUploadResult.AppNotFound, repository.uploadList(1L))
    }

    @Test
    fun `maps rejected uploads with the server message`() = runBlocking {
        val repository = repository()
        stubList()
        nextCode = 422
        nextBody = """{"message":"Item name too long"}"""

        assertEquals(PurchaseUploadResult.Rejected("Item name too long"), repository.uploadList(1L))
    }

    @Test
    fun `maps io failures to a network error`() = runBlocking {
        val repository = repository()
        stubList()
        nextException = java.io.IOException("offline")

        assertEquals(PurchaseUploadResult.NetworkError, repository.uploadList(1L))
    }

    @Test
    fun `propagates cancellation instead of mapping it to a network error`() {
        val repository = runBlocking {
            repository().also {
                stubList()
                nextException = CancellationException("cancelled")
            }
        }

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.uploadList(1L) }
        }
    }
}
