package com.example.localledger.domain.usecase

import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.repository.BudgetRepository
import javax.inject.Inject

class SaveBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(budget: Budget) = repository.save(budget)
}

class DeleteBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(ledgerId: String, budgetId: String) = repository.delete(ledgerId, budgetId)
}
