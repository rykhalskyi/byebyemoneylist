package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.LlmProfile
import com.otakeeesen.byebyemoneylist.data.LlmProvider
import com.otakeeesen.byebyemoneylist.data.agent.AgentAction
import com.otakeeesen.byebyemoneylist.data.agent.AgentChatMessage
import com.otakeeesen.byebyemoneylist.data.agent.AgentManager
import com.otakeeesen.byebyemoneylist.data.agent.AgentQuery
import com.otakeeesen.byebyemoneylist.data.agent.AgentQueryExecutor
import com.otakeeesen.byebyemoneylist.data.agent.AgentResult
import com.otakeeesen.byebyemoneylist.data.agent.MessageSender
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AgentManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    private val preferencesManager: PreferencesManager = mock()
    private val executor: AgentQueryExecutor = mock()

    private lateinit var agentManager: TestAgentManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        agentManager = TestAgentManager(preferencesManager, executor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============================================================
    // Consent gating
    // ============================================================

    @Test
    fun `consent not granted returns error response`() = runTest {
        whenever(preferencesManager.isLlmConsentGranted()).doReturn(false)
        whenever(preferencesManager.getActiveProfileId()).doReturn("profile_1")
        whenever(preferencesManager.getLlmProfiles()).doReturn(
            listOf(LlmProfile(id = "profile_1", name = "Test", provider = LlmProvider.GEMINI, apiKey = "test-key"))
        )

        val response = agentManager.processQuery("How much did I spend?")

        assertFalse(response.success)
        assertEquals("Consent is required to process purchase data through LLM APIs.", response.textResponse)
        assertNull(response.query)
        assertNull(response.result)
    }

    @Test
    fun `consent not granted when not checked still returns error`() = runTest {
        whenever(preferencesManager.isLlmConsentGranted()).doReturn(false)

        val response = agentManager.processQuery("Any question at all")

        assertFalse(response.success)
        assertTrue(response.textResponse.contains("Consent", ignoreCase = true))
    }

    // ============================================================
    // Active profile check
    // ============================================================

    @Test
    fun `no active profile returns error response`() = runTest {
        whenever(preferencesManager.isLlmConsentGranted()).doReturn(true)
        whenever(preferencesManager.getActiveProfileId()).doReturn(null)
        whenever(preferencesManager.getLlmProfiles()).doReturn(emptyList())

        val response = agentManager.processQuery("How much did I spend?")

        assertFalse(response.success)
        assertTrue(response.textResponse.contains("No active LLM profile", ignoreCase = true))
        assertNull(response.query)
        assertNull(response.result)
    }

    // ============================================================
    // REJECT_NOT_RELEVANT handling (via mock callLlm)
    // ============================================================

    @Test
    fun `REJECT_NOT_RELEVANT action returns out of scope response`() = runTest {
        whenever(preferencesManager.isLlmConsentGranted()).doReturn(true)
        whenever(preferencesManager.getActiveProfileId()).doReturn("profile_1")
        whenever(preferencesManager.getLlmProfiles()).doReturn(
            listOf(LlmProfile(id = "profile_1", name = "Test", provider = LlmProvider.GEMINI, apiKey = "test-key"))
        )

        agentManager.setMockLlmResponse("""{"action": "REJECT_NOT_RELEVANT"}""")

        val response = agentManager.processQuery("What is the weather?")

        assertTrue(response.success)
        assertEquals("I can only help with questions about your purchases, products, categories, stores, and spending.", response.textResponse)
        assertEquals(AgentResult.Error("Out of scope"), response.result)
    }

    // ============================================================
    // Successful query pipeline (with mock LLM)
    // ============================================================

    @Test
    fun `successful GET_TOTAL_SPENT returns synthesized response`() = runTest {
        whenever(preferencesManager.isLlmConsentGranted()).doReturn(true)
        whenever(preferencesManager.getActiveProfileId()).doReturn("profile_1")
        whenever(preferencesManager.getLlmProfiles()).doReturn(
            listOf(LlmProfile(id = "profile_1", name = "Test", provider = LlmProvider.GEMINI, apiKey = "test-key"))
        )
        whenever(preferencesManager.getCurrencySymbol()).doReturn("$")

        agentManager.setMockLlmResponse("""{"action": "GET_TOTAL_SPENT"}""")

        val expectedAmount = AgentResult.TotalAmount(100.0, "$", "spending", 5.0)
        whenever(executor.execute(any())).doReturn(expectedAmount)

        val response = agentManager.processQuery("How much did I spend this month?")

        assertTrue(response.success)
        assertNotNull(response.textResponse)
        assertTrue(response.textResponse.isNotBlank())
        assertNotNull(response.query)
        assertEquals(AgentAction.GET_TOTAL_SPENT, response.query?.action)
        assertEquals(expectedAmount, response.result)
    }

    @Test
    fun `executor error returns error response`() = runTest {
        whenever(preferencesManager.isLlmConsentGranted()).doReturn(true)
        whenever(preferencesManager.getActiveProfileId()).doReturn("profile_1")
        whenever(preferencesManager.getLlmProfiles()).doReturn(
            listOf(LlmProfile(id = "profile_1", name = "Test", provider = LlmProvider.GEMINI, apiKey = "test-key"))
        )

        agentManager.setMockLlmResponse("""{"action": "GET_TOTAL_SPENT"}""")

        whenever(executor.execute(any())).doReturn(AgentResult.Error("DB failure"))

        val response = agentManager.processQuery("How much did I spend this month?")

        assertFalse(response.success)
        assertEquals("I couldn't retrieve that information. Please try again.", response.textResponse)
        assertNotNull(response.query)
        assertEquals(AgentResult.Error("DB failure"), response.result)
    }

    // ============================================================
    // History context handling
    // ============================================================

    @Test
    fun `history context is passed to LLM`() = runTest {
        whenever(preferencesManager.isLlmConsentGranted()).doReturn(true)
        whenever(preferencesManager.getActiveProfileId()).doReturn("profile_1")
        whenever(preferencesManager.getLlmProfiles()).doReturn(
            listOf(LlmProfile(id = "profile_1", name = "Test", provider = LlmProvider.GEMINI, apiKey = "test-key"))
        )
        whenever(preferencesManager.getCurrencySymbol()).doReturn("$")

        val history = listOf(
            AgentChatMessage(sender = MessageSender.USER, content = "How much did I spend last month?"),
            AgentChatMessage(sender = MessageSender.ASSISTANT, content = "You spent $200 last month.")
        )

        agentManager.setMockLlmResponse("""{"action": "GET_TOTAL_SPENT"}""")
        whenever(executor.execute(any())).doReturn(AgentResult.TotalAmount(150.0, "$", "spending"))

        val response = agentManager.processQuery("And this month?", history)

        assertTrue(response.success)
        assertNotNull(response.textResponse)
    }

    // ============================================================
    // Test support: spy AgentManager with controllable callLlm
    // ============================================================

    private class TestAgentManager(
        preferencesManager: PreferencesManager,
        executor: AgentQueryExecutor
    ) : AgentManager(preferencesManager, executor) {
        private var mockLlmResponse: String? = null

        fun setMockLlmResponse(response: String) {
            mockLlmResponse = response
        }

        override suspend fun callLlm(
            profile: LlmProfile,
            systemInstruction: String,
            userMessage: String
        ): String {
            return mockLlmResponse ?: throw IllegalStateException("No mock LLM response set")
        }
    }
}
