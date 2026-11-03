package com.techpath.ai

/**
 * The single extension point for language models. GeminiClient is the only implementation today;
 * a client for the team's fine-tuned model (e.g. an OpenAI-compatible endpoint) would implement
 * this same interface and be swapped in AppContainer.
 */
interface AiClient {
    /**
     * Returns the model's text reply. Throws on any failure (no key, no network, bad response);
     * callers fall back to the dataset. With [json] = true the model is asked for a JSON reply.
     */
    suspend fun complete(system: String, user: String, json: Boolean): String
}

class NoApiKeyException : IllegalStateException("No API key configured")
