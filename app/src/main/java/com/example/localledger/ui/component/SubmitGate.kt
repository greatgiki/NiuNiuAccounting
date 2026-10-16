package com.example.localledger.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.localledger.ui.common.UiOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps a dialog open until the write finishes, and ignores extra taps while that write is running.
 * A failure stores [error] and allows another attempt. Success leaves the caller to close the dialog.
 */
class SubmitGate {
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var inFlight = false

    fun launch(scope: CoroutineScope, block: suspend () -> UiOutcome, onSuccess: () -> Unit) {
        if (inFlight) return
        inFlight = true
        submitting = true
        error = null
        scope.launch {
            val outcome = try {
                block()
            } catch (cancel: CancellationException) {
                inFlight = false
                submitting = false
                throw cancel
            } catch (thrown: Exception) {
                UiOutcome.Failure(thrown.message ?: "操作失败")
            }
            when (outcome) {
                UiOutcome.Success -> onSuccess()
                is UiOutcome.Failure -> error = outcome.message
            }
            inFlight = false
            submitting = false
        }
    }

    fun reset() {
        if (inFlight) return
        error = null
        submitting = false
    }
}
