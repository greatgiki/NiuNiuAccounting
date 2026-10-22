package com.example.localledger.domain.ai

interface AiProvider {
    suspend fun complete(endpoint: AiEndpoint, request: AiChatRequest): AiChatResponse
}
