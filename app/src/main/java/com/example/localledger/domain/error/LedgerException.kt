package com.example.localledger.domain.error

sealed class LedgerException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidName : LedgerException("名称不能为空")
    class InvalidAmount : LedgerException("金额必须大于 0 且不超过支持范围")
    class InvalidTransactionType : LedgerException("流水类型与账户或分类字段不匹配")
    class SameTransferAccount : LedgerException("转出账户和转入账户不能相同")
    class InvalidDateRange : LedgerException("结束日期不能早于开始日期")
    class LedgerNotFound : LedgerException("账本不存在")
    class LedgerArchived : LedgerException("账本已归档")
    class AccountNotFound : LedgerException("账户不存在")
    class AccountArchived : LedgerException("账户已停用")
    class AccountLedgerMismatch : LedgerException("账户不属于当前账本")
    class CategoryNotFound : LedgerException("分类不存在")
    class CategoryArchived : LedgerException("分类已停用")
    class CategoryLedgerMismatch : LedgerException("分类不属于当前账本")
    class CategoryTypeMismatch : LedgerException("分类类型与流水类型不一致")
    class TransactionNotFound : LedgerException("流水不存在")
    class TransactionLedgerMismatch : LedgerException("不能将流水移动到其他账本")
    class ArithmeticOverflow : LedgerException("金额累计超出支持范围")
    class LocalStorage(cause: Throwable? = null) : LedgerException("本地数据存储不可用", cause)
    class UnsupportedCurrency(code: String) : LedgerException("不支持的币种 $code")
    class ExchangeRateUnavailable(cause: Throwable? = null) :
        LedgerException("没有可用汇率，请联网刷新后再记外币", cause)
    class AccountCurrencyLocked : LedgerException("账户已有流水，不能修改币种")
    class BudgetNotFound : LedgerException("预算不存在")
    class DuplicateBudget : LedgerException("这个月份的预算已经存在")
    class InvalidBudget : LedgerException("预算金额必须大于 0")
    class RecurringRuleNotFound : LedgerException("周期规则不存在")
    class InvalidRecurrence : LedgerException("周期规则不完整或日期无效")
}

