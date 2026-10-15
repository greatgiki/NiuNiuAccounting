package com.example.localledger.data.repository

import android.database.sqlite.SQLiteAbortException
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import com.example.localledger.domain.error.LedgerException
import kotlinx.coroutines.CancellationException

internal suspend inline fun <T> writeOperation(crossinline block: suspend () -> T): T {
    try {
        return block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: LedgerException) {
        throw error
    } catch (error: SQLiteException) {
        throw mapSqliteFailure(error)
    } catch (error: Exception) {
        throw LedgerException.LocalStorage(error)
    }
}

internal fun mapSqliteFailure(error: SQLiteException): LedgerException {
    val message = error.message.orEmpty()
    return when {
        "cannot change account ledger" in message -> LedgerException.AccountLedgerMismatch()
        "cannot change category ledger" in message -> LedgerException.CategoryLedgerMismatch()
        "cannot retype used category" in message -> LedgerException.CategoryTypeMismatch()
        "invalid transaction" in message -> LedgerException.InvalidTransactionType()
        error is SQLiteConstraintException || error is SQLiteAbortException ->
            LedgerException.InvalidTransactionType()
        else -> LedgerException.LocalStorage(error)
    }
}

