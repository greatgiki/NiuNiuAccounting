package com.example.localledger.domain.usecase

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.TransactionRepository
import javax.inject.Inject

class SaveIncomeExpenseUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(command: SaveTransactionCommand): String {
        if (command.type == TransactionType.TRANSFER) {
            throw LedgerException.InvalidTransactionType()
        }
        return repository.saveTransaction(command)
    }
}

class SaveTransferUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(command: SaveTransactionCommand): String {
        if (command.type != TransactionType.TRANSFER) {
            throw LedgerException.InvalidTransactionType()
        }
        return repository.saveTransaction(command)
    }
}

class DeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(ledgerId: String, transactionId: String) {
        repository.deleteTransaction(ledgerId, transactionId)
    }
}
