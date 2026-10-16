package com.example.localledger.data.local.converter

import androidx.room.TypeConverter
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.TransactionType

class RoomConverters {
    @TypeConverter
    fun accountTypeToString(value: AccountType): String = value.name

    @TypeConverter
    fun stringToAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun categoryTypeToString(value: CategoryType): String = value.name

    @TypeConverter
    fun stringToCategoryType(value: String): CategoryType = CategoryType.valueOf(value)

    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun recurrencePeriodToString(value: RecurrencePeriod): String = value.name

    @TypeConverter
    fun stringToRecurrencePeriod(value: String): RecurrencePeriod = RecurrencePeriod.valueOf(value)
}

