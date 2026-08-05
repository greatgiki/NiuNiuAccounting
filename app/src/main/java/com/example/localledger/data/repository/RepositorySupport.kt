package com.example.localledger.data.repository

import com.example.localledger.domain.error.LedgerException

internal suspend inline fun <T> writeOperation(crossinline block: suspend () -> T): T = try {
    block()
} catch (error: LedgerException) {
    throw error
} catch (error: Exception) {
    throw LedgerException.LocalStorage(error)
}

