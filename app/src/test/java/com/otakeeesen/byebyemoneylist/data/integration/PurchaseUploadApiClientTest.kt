package com.otakeeesen.byebyemoneylist.data.integration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseUploadApiClientTest {

    private val payload = PurchaseUploadRequest(
        clientId = "client-1",
        listId = "list-1",
        list = PurchaseUploadList(name = "Weekly"),
        items = listOf(PurchaseUploadItem(lineId = "1", name = "Milk", quantity = 1.0, unitPrice = 1.29)),
    )

    private class FakeReply(
        val code: Int = 200,
        val body: String = "{}",
        val exception: Throwable? = null,
    )

    private var lastRequest: Request? = null

    private fun client(handler: (Request) -> FakeReply): PurchaseUploadApiClient {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                lastRequest = request
                val reply = handler(request)
                reply.exception?.let { throw it }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(reply.code)
                    .message("test")
                    .body(reply.body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return PurchaseUploadApiClient(httpClient)
    }

    @Test
    fun `posts the contract to the ocs path with basic auth and json headers`() {
        runBlocking {
            val client = client {
                FakeReply(
                    code = 200,
                    body = """{"import":{"id":"imp-1","clientListId":"list-1","status":"incoming"},"duplicate":false}""",
                )
            }

            val result = client.upload("https://cloud.example.com", "user", "pass", payload).getOrThrow()

            assertEquals("imp-1", result.importId)
            assertFalse(result.alreadyImported)

            val request = requireNotNull(lastRequest)
            assertEquals(
                "https://cloud.example.com/ocs/v2.php/apps/byebyemoneylist/api/purchase-imports",
                request.url.toString(),
            )
            assertEquals("POST", request.method)
            assertEquals("Basic dXNlcjpwYXNz", request.header("Authorization"))
            assertEquals("true", request.header("OCS-APIRequest"))
            assertEquals("application/json", request.header("Accept"))
            assertTrue(request.body?.contentType()?.toString()?.startsWith("application/json") == true)
        }
    }

    @Test
    fun `parses ocs wrapped upload responses`() {
        runBlocking {
            val client = client {
                FakeReply(
                    code = 200,
                    body = """{"ocs":{"meta":{"status":"ok"},"data":{"import":{"id":"imp-2"},"duplicate":false}}}""",
                )
            }

            val result = client.upload("https://cloud.example.com", "user", "pass", payload).getOrThrow()

            assertEquals("imp-2", result.importId)
            assertFalse(result.alreadyImported)
        }
    }

    @Test
    fun `409 maps to already imported without throwing`() {
        runBlocking {
            val client = client { FakeReply(code = 409, body = """{"duplicate":true}""") }

            val result = client.upload("https://cloud.example.com", "user", "pass", payload).getOrThrow()

            assertTrue(result.alreadyImported)
        }
    }

    @Test
    fun `401 and 403 map to auth exceptions`() {
        runBlocking {
            listOf(401, 403).forEach { code ->
                val client = client { FakeReply(code = code) }
                assertThrows(PurchaseUploadAuthException::class.java) {
                    runBlocking { client.upload("https://cloud.example.com", "user", "pass", payload) }
                        .getOrThrow()
                }
            }
        }
    }

    @Test
    fun `404 maps to app not found`() {
        runBlocking {
            val client = client { FakeReply(code = 404) }

            assertThrows(PurchaseUploadAppNotFoundException::class.java) {
                runBlocking { client.upload("https://cloud.example.com", "user", "pass", payload) }.getOrThrow()
            }
        }
    }

    @Test
    fun `422 maps to rejected with the server message`() {
        runBlocking {
            val client = client {
                FakeReply(code = 422, body = """{"message":"Item name too long"}""")
            }

            val error = assertThrows(PurchaseUploadRejectedException::class.java) {
                runBlocking { client.upload("https://cloud.example.com", "user", "pass", payload) }.getOrThrow()
            }

            assertEquals("Item name too long", error.message)
        }
    }

    @Test
    fun `io failures map to a network exception`() {
        runBlocking {
            val client = client { FakeReply(exception = java.io.IOException("offline")) }

            assertThrows(PurchaseUploadNetworkException::class.java) {
                runBlocking { client.upload("https://cloud.example.com", "user", "pass", payload) }.getOrThrow()
            }
        }
    }

    @Test
    fun `cancellation is propagated instead of being wrapped`() {
        runBlocking {
            val client = client { FakeReply(exception = CancellationException("cancelled")) }

            assertThrows(CancellationException::class.java) {
                runBlocking { client.upload("https://cloud.example.com", "user", "pass", payload) }.getOrThrow()
            }
        }
    }

    @Test
    fun `test connection succeeds on 2xx and reports auth errors`() {
        runBlocking {
            val ok = client { FakeReply(code = 200, body = "{}") }
            ok.testConnection("https://cloud.example.com", "user", "pass").getOrThrow()

            val unauthorized = client { FakeReply(code = 401) }
            assertThrows(PurchaseUploadAuthException::class.java) {
                runBlocking { unauthorized.testConnection("https://cloud.example.com", "user", "pass") }.getOrThrow()
            }
        }
    }

    @Test
    fun `bare host defaults to https and trailing slash is trimmed`() {
        runBlocking {
            val client = client { FakeReply(code = 200, body = """{"import":{"id":"imp-3"}}""") }

            client.upload("cloud.example.com/", "user", "pass", payload).getOrThrow()

            assertEquals(
                "https://cloud.example.com/ocs/v2.php/apps/byebyemoneylist/api/purchase-imports",
                lastRequest?.url.toString(),
            )
        }
    }
}
