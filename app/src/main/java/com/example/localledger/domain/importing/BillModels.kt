package com.example.localledger.domain.importing

import com.example.localledger.domain.model.TransactionType

enum class BillSource {
    WECHAT,
    ALIPAY,
    ;

    fun label(): String = when (this) {
        WECHAT -> "微信支付"
        ALIPAY -> "支付宝"
    }
}

object RecordOrigin {
    const val MANUAL = "MANUAL"
    const val IMPORT = "IMPORT"
    const val NOTIFICATION = "NOTIFICATION"
}

object DraftStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val DISMISSED = "DISMISSED"
    const val MERGED = "MERGED"
}

enum class RowSkipReason {
    FAILED_OR_CLOSED,
    NEUTRAL,
    INVALID_AMOUNT,
    INVALID_TIME,
    EMPTY,
}

enum class DuplicateFlag {
    NONE,
    ALREADY_IMPORTED,
    IN_FILE_DUPLICATE,
    LIKELY_DUPLICATE,
}

enum class CandidateKind {
    TRANSACTION,
    DRAFT,
}

data class ParsedBillRow(
    val source: BillSource,
    val type: TransactionType,
    val amountMinor: Long,
    val transactionAt: Long,
    val counterparty: String,
    val goods: String,
    val paymentMethod: String,
    val status: String,
    val externalId: String,
    val merchantOrderId: String,
    val note: String,
    val keywordText: String,
)

data class SkippedBillRow(
    val reason: RowSkipReason,
    val summary: String,
)

data class BillParseResult(
    val source: BillSource,
    val rows: List<ParsedBillRow>,
    val skipped: List<SkippedBillRow>,
)

data class ExternalKey(
    val source: String,
    val externalId: String,
)

data class MatchCandidate(
    val id: String,
    val kind: CandidateKind,
    val type: TransactionType,
    val amountMinor: Long,
    val transactionAt: Long,
    val origin: String,
    val externalSource: String?,
    val externalId: String?,
)

data class FuzzyHit(
    val candidateId: String,
    val kind: CandidateKind,
    val origin: String,
    val timeDeltaMillis: Long,
)

data class MatchResult(
    val flag: DuplicateFlag,
    val hit: FuzzyHit?,
)

class BillParseException(message: String) : Exception(message)
