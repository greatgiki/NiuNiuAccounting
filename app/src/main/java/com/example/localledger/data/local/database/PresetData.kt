package com.example.localledger.data.local.database

import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.data.local.entity.LedgerEntity
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType

object PresetData {
    const val DEFAULT_LEDGER_ID = "00000000-0000-4000-8000-000000000001"

    fun ledger(id: String, name: String, now: Long) = LedgerEntity(
        id = id,
        name = name,
        currencyCode = "CNY",
        createdAt = now,
    )

    fun accounts(ledgerId: String, now: Long): List<AccountEntity> = listOf(
        account(ledgerId, "现金", AccountType.CASH, 0, now),
        account(ledgerId, "银行卡", AccountType.BANK_CARD, 1, now),
        account(ledgerId, "支付宝", AccountType.E_WALLET, 2, now),
        account(ledgerId, "微信", AccountType.E_WALLET, 3, now),
    )

    fun categories(ledgerId: String, now: Long): List<CategoryEntity> {
        val expenses = listOf(
            "餐饮" to "restaurant",
            "交通" to "directions_car",
            "购物" to "shopping_bag",
            "居住" to "home",
            "娱乐" to "sports_esports",
            "医疗" to "medical_services",
            "教育" to "school",
            "通讯" to "phone_android",
            "其他" to "more_horiz",
        ).mapIndexed { index, (name, icon) ->
            category(ledgerId, name, CategoryType.EXPENSE, icon, index, now)
        }
        val incomes = listOf(
            "工资" to "payments",
            "奖金" to "redeem",
            "兼职" to "work",
            "退款" to "undo",
            "其他" to "more_horiz",
        ).mapIndexed { index, (name, icon) ->
            category(ledgerId, name, CategoryType.INCOME, icon, index, now)
        }
        return expenses + incomes
    }

    private fun account(
        ledgerId: String,
        name: String,
        type: AccountType,
        sortOrder: Int,
        now: Long,
    ) = AccountEntity(
        id = stableId(ledgerId, "account", sortOrder),
        ledgerId = ledgerId,
        name = name,
        type = type,
        sortOrder = sortOrder,
        createdAt = now,
        updatedAt = now,
    )

    private fun category(
        ledgerId: String,
        name: String,
        type: CategoryType,
        iconKey: String,
        sortOrder: Int,
        now: Long,
    ) = CategoryEntity(
        id = stableId(ledgerId, "category-${type.name.lowercase()}", sortOrder),
        ledgerId = ledgerId,
        name = name,
        type = type,
        iconKey = iconKey,
        sortOrder = sortOrder,
        createdAt = now,
        updatedAt = now,
    )

    private fun stableId(ledgerId: String, kind: String, index: Int): String =
        java.util.UUID.nameUUIDFromBytes("$ledgerId:$kind:$index".toByteArray()).toString()
}

