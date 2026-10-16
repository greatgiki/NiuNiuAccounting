package com.example.localledger.ui.common

import com.example.localledger.domain.error.LedgerException
import kotlinx.coroutines.CancellationException

sealed interface UiOutcome {
    data object Success : UiOutcome
    data class Failure(val message: String) : UiOutcome
}

internal suspend inline fun runOutcome(
    fallback: String,
    crossinline block: suspend () -> Unit,
): UiOutcome = try {
    block()
    UiOutcome.Success
} catch (error: CancellationException) {
    throw error
} catch (error: LedgerException) {
    UiOutcome.Failure(error.message ?: fallback)
}
