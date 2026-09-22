package com.otakeeesen.byebyemoneylist.data.agent

/**
 * Minimal seam for a one-shot text LLM completion, so features can depend on the
 * LLM without pulling in the full query/executor graph.
 */
interface TextCompletion {
    suspend fun complete(systemInstruction: String, userMessage: String): String?
}
