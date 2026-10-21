package com.example.localledger.data.repository

import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.local.entity.MerchantCategoryRuleEntity
import com.example.localledger.domain.importing.MerchantKey
import com.example.localledger.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class MerchantCategoryRule(
    val id: String,
    val merchantLabel: String,
    val type: TransactionType,
    val categoryId: String,
    val categoryName: String,
    val updatedAt: Long,
)

@Singleton
class MerchantCategoryBook @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
    private val clock: Clock,
) {
    suspend fun findCategoryId(ledgerId: String, type: TransactionType, merchant: String): String? {
        val key = MerchantKey.normalize(merchant)
        if (key.isEmpty() || type == TransactionType.TRANSFER) return null
        return databaseProvider.get().merchantCategoryDao().find(ledgerId, key, type)?.categoryId
    }

    suspend fun keys(ledgerId: String, type: TransactionType): Map<String, String> {
        if (type == TransactionType.TRANSFER) return emptyMap()
        return databaseProvider.get().merchantCategoryDao().keys(ledgerId, type)
            .associate { it.merchantKey to it.categoryId }
    }

    suspend fun remember(ledgerId: String, type: TransactionType, merchant: String, categoryId: String) {
        val key = MerchantKey.normalize(merchant)
        val label = MerchantKey.display(merchant)
        if (key.isEmpty() || label.isEmpty() || type == TransactionType.TRANSFER) return
        val dao = databaseProvider.get().merchantCategoryDao()
        val existing = dao.find(ledgerId, key, type)
        val now = clock.millis()
        if (existing == null) {
            dao.upsert(
                MerchantCategoryRuleEntity(
                    id = UUID.randomUUID().toString(),
                    ledgerId = ledgerId,
                    merchantKey = key,
                    merchantLabel = label,
                    type = type,
                    categoryId = categoryId,
                    updatedAt = now,
                ),
            )
        } else if (existing.categoryId != categoryId || existing.merchantLabel != label) {
            dao.upsert(existing.copy(categoryId = categoryId, merchantLabel = label, updatedAt = now))
        }
    }

    fun observe(ledgerId: String): Flow<List<MerchantCategoryRule>> = flow {
        emitAll(
            databaseProvider.get().merchantCategoryDao().observe(ledgerId).map { rows ->
                rows.map { row ->
                    MerchantCategoryRule(
                        id = row.rule.id,
                        merchantLabel = row.rule.merchantLabel,
                        type = row.rule.type,
                        categoryId = row.rule.categoryId,
                        categoryName = row.categoryName,
                        updatedAt = row.rule.updatedAt,
                    )
                }
            },
        )
    }.flowOn(Dispatchers.IO)

    suspend fun clear(ledgerId: String, id: String) {
        databaseProvider.get().merchantCategoryDao().delete(id, ledgerId)
    }

    suspend fun clearAll(ledgerId: String) {
        databaseProvider.get().merchantCategoryDao().deleteAll(ledgerId)
    }
}
