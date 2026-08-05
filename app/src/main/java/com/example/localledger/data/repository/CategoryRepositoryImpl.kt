package com.example.localledger.data.repository

import androidx.room.withTransaction
import com.example.localledger.data.local.database.DatabaseProvider
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.data.mapper.toEntity
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val databaseProvider: DatabaseProvider,
    private val clock: Clock,
) : CategoryRepository {
    override fun observeCategories(ledgerId: String, type: CategoryType): Flow<List<Category>> = flow {
        emitAll(databaseProvider.get().categoryDao().observeByLedgerAndType(ledgerId, type).map { rows ->
            rows.map { it.toDomain() }
        })
    }

    override suspend fun saveCategory(category: Category) = writeOperation {
        val normalizedName = category.name.trim().ifEmpty { throw LedgerException.InvalidName() }
        if (category.iconKey.isBlank()) throw LedgerException.InvalidName()
        val database = databaseProvider.get()
        database.withTransaction {
            val ledger = database.ledgerDao().findById(category.ledgerId)
                ?: throw LedgerException.LedgerNotFound()
            if (ledger.isArchived) throw LedgerException.LedgerArchived()
            val existing = database.categoryDao().findById(category.id)
            if (existing != null && existing.ledgerId != category.ledgerId) {
                throw LedgerException.CategoryLedgerMismatch()
            }
            if (existing != null && existing.type != category.type) {
                throw LedgerException.CategoryTypeMismatch()
            }
            val now = clock.millis()
            database.categoryDao().upsert(
                category.copy(
                    name = normalizedName,
                    iconKey = category.iconKey.trim(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ).toEntity(),
            )
        }
    }

    override suspend fun archiveCategory(categoryId: String) = writeOperation {
        val database = databaseProvider.get()
        database.withTransaction {
            database.categoryDao().findById(categoryId) ?: throw LedgerException.CategoryNotFound()
            database.categoryDao().archive(categoryId, clock.millis())
        }
        Unit
    }
}
