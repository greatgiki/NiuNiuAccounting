package com.example.localledger.di

import com.example.localledger.data.local.database.DatabaseProvider
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.remote.HttpExchangeRateRemote
import com.example.localledger.data.repository.AccountRepositoryImpl
import com.example.localledger.data.repository.BudgetRepositoryImpl
import com.example.localledger.data.repository.CategoryRepositoryImpl
import com.example.localledger.data.repository.ExchangeRateRepositoryImpl
import com.example.localledger.data.repository.LedgerRepositoryImpl
import com.example.localledger.data.repository.RecurringRuleRepositoryImpl
import com.example.localledger.data.repository.StatisticsRepositoryImpl
import com.example.localledger.data.repository.TransactionRepositoryImpl
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.BudgetRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.domain.repository.ExchangeRateRemote
import com.example.localledger.domain.repository.ExchangeRateRepository
import com.example.localledger.domain.repository.LedgerRepository
import com.example.localledger.domain.repository.RecurringRuleRepository
import com.example.localledger.domain.repository.StatisticsRepository
import com.example.localledger.domain.repository.TransactionRepository
import com.example.localledger.domain.time.SystemZoneIdProvider
import com.example.localledger.domain.time.ZoneIdProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun walletDatabaseSource(impl: DatabaseProvider): WalletDatabaseSource

    @Binds abstract fun ledgerRepository(impl: LedgerRepositoryImpl): LedgerRepository
    @Binds abstract fun accountRepository(impl: AccountRepositoryImpl): AccountRepository
    @Binds abstract fun categoryRepository(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds abstract fun transactionRepository(impl: TransactionRepositoryImpl): TransactionRepository
    @Binds abstract fun statisticsRepository(impl: StatisticsRepositoryImpl): StatisticsRepository
    @Binds abstract fun budgetRepository(impl: BudgetRepositoryImpl): BudgetRepository
    @Binds abstract fun recurringRuleRepository(impl: RecurringRuleRepositoryImpl): RecurringRuleRepository
    @Binds abstract fun exchangeRateRepository(impl: ExchangeRateRepositoryImpl): ExchangeRateRepository
    @Binds abstract fun exchangeRateRemote(impl: HttpExchangeRateRemote): ExchangeRateRemote
    @Binds abstract fun zoneIdProvider(impl: SystemZoneIdProvider): ZoneIdProvider
}

@Module
@InstallIn(SingletonComponent::class)
object BackendModule {
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, error ->
            if (error is CancellationException) return@CoroutineExceptionHandler
            Log.e("LocalLedger", "未捕获的协程异常", error)
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }
}
