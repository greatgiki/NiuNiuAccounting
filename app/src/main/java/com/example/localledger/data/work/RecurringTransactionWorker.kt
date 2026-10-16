package com.example.localledger.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.localledger.domain.usecase.GenerateRecurringTransactionsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class RecurringTransactionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val generator = EntryPointAccessors.fromApplication(
            applicationContext,
            RecurringWorkerEntryPoint::class.java,
        ).generator()
        return try {
            generator()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecurringWorkerEntryPoint {
    fun generator(): GenerateRecurringTransactionsUseCase
}

object RecurringWorkScheduler {
    private const val UNIQUE_NAME = "recurring-transactions"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
