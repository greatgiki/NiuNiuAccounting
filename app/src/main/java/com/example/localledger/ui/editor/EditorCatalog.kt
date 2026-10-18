package com.example.localledger.ui.editor

import com.example.localledger.di.ApplicationScope
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.ui.AppSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class AccountSnapshot(
    val all: List<Account>,
    val active: List<Account>,
    val loaded: Boolean,
) {
    companion object {
        val EMPTY = AccountSnapshot(emptyList(), emptyList(), loaded = false)
    }
}

data class CategorySnapshot(
    val all: List<Category>,
    val active: List<Category>,
    val rows: List<List<Category>>,
    val loaded: Boolean,
) {
    companion object {
        val EMPTY = CategorySnapshot(emptyList(), emptyList(), emptyList(), loaded = false)
    }
}

internal fun categoryRows(categories: List<Category>): List<List<Category>> = categories.chunked(4)

/**
 * Accounts and categories are observed once for the process, off the main thread,
 * so opening 记一笔 can paint from memory instead of waiting on a new Room subscription.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EditorCatalog @Inject constructor(
    appSession: AppSession,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val ledgerId = appSession.state
        .map { it.currentLedgerId }
        .distinctUntilChanged()

    val accounts: StateFlow<AccountSnapshot> = ledgerId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(AccountSnapshot(emptyList(), emptyList(), loaded = true))
            } else {
                accountRepository.observeAccounts(id).map { list ->
                    AccountSnapshot(
                        all = list,
                        active = list.filter { !it.isArchived },
                        loaded = true,
                    )
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, AccountSnapshot.EMPTY)

    val expenseCategories: StateFlow<CategorySnapshot> =
        categories(categoryRepository, CategoryType.EXPENSE, scope)

    val incomeCategories: StateFlow<CategorySnapshot> =
        categories(categoryRepository, CategoryType.INCOME, scope)

    fun categoriesFor(type: TransactionType): StateFlow<CategorySnapshot> = when (type) {
        TransactionType.INCOME -> incomeCategories
        else -> expenseCategories
    }

    fun warm() {
        accounts.value
        expenseCategories.value
        incomeCategories.value
    }

    private fun categories(
        categoryRepository: CategoryRepository,
        type: CategoryType,
        scope: CoroutineScope,
    ): StateFlow<CategorySnapshot> = ledgerId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(CategorySnapshot(emptyList(), emptyList(), emptyList(), loaded = true))
            } else {
                categoryRepository.observeCategories(id, type).map { list ->
                    val active = list.filter { !it.isArchived }
                    CategorySnapshot(
                        all = list,
                        active = active,
                        rows = categoryRows(active),
                        loaded = true,
                    )
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, CategorySnapshot.EMPTY)
}
