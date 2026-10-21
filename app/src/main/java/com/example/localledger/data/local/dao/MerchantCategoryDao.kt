package com.example.localledger.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import com.example.localledger.data.local.entity.MerchantCategoryRuleEntity
import com.example.localledger.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

data class MerchantCategoryRuleRow(
    @Embedded val rule: MerchantCategoryRuleEntity,
    @ColumnInfo(name = "category_name") val categoryName: String,
)

@Dao
interface MerchantCategoryDao {
    @Query(
        """
        SELECT * FROM merchant_category_rules
        WHERE ledger_id = :ledgerId AND merchant_key = :merchantKey AND type = :type
        LIMIT 1
        """,
    )
    suspend fun find(ledgerId: String, merchantKey: String, type: TransactionType): MerchantCategoryRuleEntity?

    @Query(
        """
        SELECT merchant_key, category_id FROM merchant_category_rules
        WHERE ledger_id = :ledgerId AND type = :type
        """,
    )
    suspend fun keys(ledgerId: String, type: TransactionType): List<MerchantCategoryKeyRow>

    @Upsert
    suspend fun upsert(entity: MerchantCategoryRuleEntity)

    @Query(
        """
        SELECT r.*, c.name AS category_name
        FROM merchant_category_rules r
        JOIN categories c ON c.id = r.category_id
        WHERE r.ledger_id = :ledgerId
        ORDER BY r.updated_at DESC, r.merchant_label ASC
        """,
    )
    fun observe(ledgerId: String): Flow<List<MerchantCategoryRuleRow>>

    @Query("DELETE FROM merchant_category_rules WHERE id = :id AND ledger_id = :ledgerId")
    suspend fun delete(id: String, ledgerId: String): Int

    @Query("DELETE FROM merchant_category_rules WHERE ledger_id = :ledgerId")
    suspend fun deleteAll(ledgerId: String)
}

data class MerchantCategoryKeyRow(
    @ColumnInfo(name = "merchant_key") val merchantKey: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
)
