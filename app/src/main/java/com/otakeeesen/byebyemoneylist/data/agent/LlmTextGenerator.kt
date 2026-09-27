package com.otakeeesen.byebyemoneylist.data.agent

/**
 * Minimal text-generation seam over the configured LLM, decoupling consumers
 * (e.g. [ToBuyAutoMatcher]) from [AgentManager] and its query-execution graph.
 *
 * Implementations must return `null` when no active LLM profile is configured
 * or when the call fails, and must never throw.
 */
interface LlmTextGenerator {
    suspend fun generate(systemInstruction: String, userMessage: String): String?
}
