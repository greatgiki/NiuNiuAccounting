package com.example.localledger.data.ai

import com.example.localledger.domain.ai.AiAccess
import com.example.localledger.domain.ai.AiCallException
import com.example.localledger.domain.ai.AiChatRequest
import com.example.localledger.domain.ai.AiChatResponse
import com.example.localledger.domain.ai.AiCompleter
import com.example.localledger.domain.ai.AiEndpoint
import com.example.localledger.domain.ai.AiProvider
import com.example.localledger.security.AppLockController
import com.example.localledger.security.AppLockState
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class AiGateway @Inject constructor(
    private val provider: AiProvider,
    private val settingsStore: AiSettingsStore,
    private val credentials: AiCredentialStore,
    private val usage: AiUsageStore,
    private val lock: AppLockController,
) : AiCompleter {
    override suspend fun complete(request: AiChatRequest): AiChatResponse =
        dispatch(request, keyOverride = null, requireEnabled = true)

    suspend fun test(request: AiChatRequest, keyOverride: String?): AiChatResponse =
        dispatch(request, keyOverride, requireEnabled = false)

    private suspend fun dispatch(
        request: AiChatRequest,
        keyOverride: String?,
        requireEnabled: Boolean,
    ): AiChatResponse {
        val settings = settingsStore.current()
        val stored = credentials.read()
        val override = keyOverride?.trim()?.ifBlank { null }
        val secret = override ?: (stored as? CredentialRead.Present)?.key
        val cap = settings.dailyRequestCap
        val used = if (cap > 0) usage.usedToday() else 0
        val failure = AiAccess.check(
            locked = lock.state.value.phase != AppLockState.Phase.Unlocked,
            enabled = settings.enabled,
            requireEnabled = requireEnabled,
            apiKey = secret,
            keyUnreadable = stored is CredentialRead.Unreadable,
            model = settings.model,
            baseUrl = settings.baseUrl,
            cap = cap,
            used = used,
        )
        if (failure != null) throw AiCallException(failure)
        val endpoint = AiEndpoint(
            format = settings.format,
            baseUrl = settings.baseUrl,
            model = settings.model,
            apiKey = secret.orEmpty(),
        )
        return try {
            provider.complete(endpoint, request)
        } finally {
            if (cap > 0 && coroutineContext.isActive) usage.increment()
        }
    }
}
