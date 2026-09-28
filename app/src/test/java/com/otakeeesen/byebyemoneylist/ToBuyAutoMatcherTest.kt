package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.LlmProfile
import com.otakeeesen.byebyemoneylist.data.LlmProvider
import com.otakeeesen.byebyemoneylist.data.agent.LlmTextGenerator
import com.otakeeesen.byebyemoneylist.data.agent.ToBuyAutoMatcher
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ToBuyAutoMatcherTest {

    private val profile = LlmProfile(id = "p1", name = "Test", provider = LlmProvider.GEMINI, apiKey = "key")

    private fun activePreferences(): PreferencesManager {
        val prefs = mock<PreferencesManager>()
        whenever(prefs.getActiveProfileId()).thenReturn("p1")
        whenever(prefs.getLlmProfiles()).thenReturn(listOf(profile))
        return prefs
    }

    private class RecordingGenerator(private val response: String?) : LlmTextGenerator {
        var calls = 0
        var lastSystem: String? = null
        var lastUser: String? = null
        override suspend fun generate(systemInstruction: String, userMessage: String): String? {
            calls++
            lastSystem = systemInstruction
            lastUser = userMessage
            return response
        }
    }

    @Test
    fun `returns empty and does not call llm when no active profile`() = runBlocking<Unit> {
        val prefs = mock<PreferencesManager>()
        whenever(prefs.getActiveProfileId()).thenReturn(null)
        val llm = RecordingGenerator("""{"matchedIds":[1]}""")

        val result = ToBuyAutoMatcher(prefs, llm).match(listOf(1L to "Milk"), listOf("milk"))

        assertTrue(result.isEmpty())
        assertEquals(0, llm.calls)
    }

    @Test
    fun `matches with an active profile even when consent is not granted`() = runBlocking<Unit> {
        val prefs = mock<PreferencesManager>()
        whenever(prefs.getActiveProfileId()).thenReturn("p1")
        whenever(prefs.getLlmProfiles()).thenReturn(listOf(profile))
        whenever(prefs.isLlmConsentGranted()).thenReturn(false)
        val llm = RecordingGenerator("""{"matchedIds":[1]}""")

        val result = ToBuyAutoMatcher(prefs, llm).match(listOf(1L to "Milk"), listOf("milk"))

        assertEquals(listOf(1L), result)
        assertEquals(1, llm.calls)
    }

    @Test
    fun `returns empty and does not call llm when active id is not among profiles`() = runBlocking<Unit> {
        val prefs = mock<PreferencesManager>()
        whenever(prefs.getActiveProfileId()).thenReturn("missing")
        whenever(prefs.getLlmProfiles()).thenReturn(listOf(profile))
        val llm = RecordingGenerator("""{"matchedIds":[1]}""")

        val result = ToBuyAutoMatcher(prefs, llm).match(listOf(1L to "Milk"), listOf("milk"))

        assertTrue(result.isEmpty())
        assertEquals(0, llm.calls)
    }

    @Test
    fun `returns empty and does not call llm when pending or purchased is empty`() = runBlocking<Unit> {
        val prefs = activePreferences()
        val llm = RecordingGenerator("""{"matchedIds":[1]}""")
        val matcher = ToBuyAutoMatcher(prefs, llm)

        assertTrue(matcher.match(emptyList(), listOf("milk")).isEmpty())
        assertTrue(matcher.match(listOf(1L to "Milk"), emptyList()).isEmpty())
        assertEquals(0, llm.calls)
    }

    @Test
    fun `parses matched ids and sends both lists in the prompt`() = runBlocking<Unit> {
        val prefs = activePreferences()
        val llm = RecordingGenerator("""{"matchedIds":[1]}""")

        val result = ToBuyAutoMatcher(prefs, llm).match(
            listOf(1L to "молоко", 2L to "яйця"),
            listOf("milk", "bread")
        )

        assertEquals(listOf(1L), result)
        assertEquals(1, llm.calls)
        assertTrue(llm.lastUser!!.contains("milk"))
        assertTrue(llm.lastUser!!.contains("1: молоко"))
        assertTrue(llm.lastSystem!!.contains("matchedIds"))
    }

    @Test
    fun `strips markdown fences from the response`() = runBlocking<Unit> {
        val prefs = activePreferences()
        val llm = RecordingGenerator("```json\n{\"matchedIds\":[2]}\n```")

        val result = ToBuyAutoMatcher(prefs, llm).match(listOf(1L to "Milk", 2L to "Bread"), listOf("bread"))

        assertEquals(listOf(2L), result)
    }

    @Test
    fun `filters out ids that were not in the pending list and de-duplicates`() = runBlocking<Unit> {
        val prefs = activePreferences()
        val llm = RecordingGenerator("""{"matchedIds":[1,1,999]}""")

        val result = ToBuyAutoMatcher(prefs, llm).match(listOf(1L to "Milk"), listOf("milk"))

        assertEquals(listOf(1L), result)
    }

    @Test
    fun `malformed json yields empty result`() = runBlocking<Unit> {
        val prefs = activePreferences()
        val llm = RecordingGenerator("I cannot help with that")

        val result = ToBuyAutoMatcher(prefs, llm).match(listOf(1L to "Milk"), listOf("milk"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `rethrows cancellation from the llm call`() = runBlocking<Unit> {
        val prefs = activePreferences()
        val llm = object : LlmTextGenerator {
            override suspend fun generate(systemInstruction: String, userMessage: String): String? {
                throw CancellationException("cancelled")
            }
        }

        val outcome = runCatching {
            ToBuyAutoMatcher(prefs, llm).match(listOf(1L to "Milk"), listOf("milk"))
        }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
    }
}
