package com.example.localledger.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.example.localledger.core.storage.SettingsStore
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.local.entity.PendingDraftEntity
import com.example.localledger.domain.capture.CaptureDedup
import com.example.localledger.domain.capture.FillCandidate
import com.example.localledger.domain.importing.AccountHintMapper
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.importing.CandidateKind
import com.example.localledger.domain.importing.CaptureGate
import com.example.localledger.domain.importing.CaptureSettings
import com.example.localledger.domain.importing.CategoryGuess
import com.example.localledger.domain.importing.DedupDecision
import com.example.localledger.domain.importing.DedupExisting
import com.example.localledger.domain.importing.DedupIncoming
import com.example.localledger.domain.importing.DuplicateFlag
import com.example.localledger.domain.importing.ExternalKey
import com.example.localledger.domain.importing.ImportMatcher
import com.example.localledger.domain.importing.MatchCandidate
import com.example.localledger.domain.importing.NotificationCapturePolicy
import com.example.localledger.domain.importing.PaymentNotificationParser
import com.example.localledger.domain.importing.RecordOrigin
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class CapturedNotification(
    val packageName: String,
    val text: String,
    val postedAt: Long,
    val key: String,
    val postTime: Long = postedAt,
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
)

sealed interface NotificationRecordResult {
    data class Created(
        val draftId: String,
        val source: BillSource,
        val type: TransactionType,
        val amountMinor: Long,
        val counterparty: String,
        val autoConfirmed: Boolean,
        val needsAssignment: Boolean,
        val needsAmount: Boolean,
        val patternId: String,
        val openBill: Boolean = false,
        val sourceKey: String = "",
    ) : NotificationRecordResult

    data class Enriched(
        val draftId: String,
        val source: BillSource,
        val type: TransactionType,
        val amountMinor: Long,
        val counterparty: String,
        val autoConfirmed: Boolean,
        val patternId: String,
    ) : NotificationRecordResult

    data class Ignored(val reason: String) : NotificationRecordResult
    data class Disabled(val reason: String) : NotificationRecordResult
    data class Duplicate(val reason: String, val cancelDraftId: String? = null) : NotificationRecordResult
    data class Failed(val reason: String) : NotificationRecordResult
}

fun NotificationRecordResult.describe(): String = when (this) {
    is NotificationRecordResult.Created -> when {
        autoConfirmed -> "已自动记账"
        needsAmount -> "已生成待确认（需补全金额）"
        else -> "已生成待确认"
    }
    is NotificationRecordResult.Enriched -> if (autoConfirmed) "已补全金额并自动记账" else "已补全金额"
    is NotificationRecordResult.Ignored -> "已忽略：$reason"
    is NotificationRecordResult.Disabled -> "未处理：$reason"
    is NotificationRecordResult.Duplicate -> "已忽略：$reason"
    is NotificationRecordResult.Failed -> "写入失败：$reason"
}

@Singleton
class PaymentDraftWriter @Inject constructor(
    private val settingsStore: SettingsStore,
    private val databaseProvider: WalletDatabaseSource,
    private val draftRepository: DraftRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val merchantCategories: MerchantCategoryBook,
    private val clock: Clock,
) {
    private val recordMutex = Mutex()

    suspend fun record(event: CapturedNotification): NotificationRecordResult = recordMutex.withLock {
        try {
            recordLocked(event)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "通知记账写入失败", error)
            NotificationRecordResult.Failed(error.message ?: "未知错误")
        }
    }

    private suspend fun recordLocked(event: CapturedNotification): NotificationRecordResult {
        val settings = CaptureSettings.from(settingsStore.peek())
        when (val gate = NotificationCapturePolicy.gate(event.packageName, settings)) {
            is CaptureGate.Disabled -> return NotificationRecordResult.Disabled(gate.reason)
            is CaptureGate.Ignored -> return NotificationRecordResult.Ignored(gate.reason)
            is CaptureGate.Open -> {
                if (event.text.isBlank()) return NotificationRecordResult.Ignored("文本为空")
                val parsed = PaymentNotificationParser.parse(gate.source, event.text)
                    ?: return NotificationRecordResult.Ignored("不是支付通知")
                if (!NotificationCapturePolicy.amountlessAllowed(parsed.needsAmount, settings)) {
                    return NotificationRecordResult.Ignored("金额缺失，已关闭无金额草稿")
                }
                val loaded = settingsStore.peek() ?: return NotificationRecordResult.Disabled("设置尚未读取")
                return saveParsed(event, loaded, parsed)
            }
        }
    }

    private suspend fun saveParsed(
        event: CapturedNotification,
        settings: com.example.localledger.core.storage.AppSettings,
        parsed: com.example.localledger.domain.importing.ParsedNotification,
    ): NotificationRecordResult {
        val ledgerId = settings.currentLedgerId
        val dedupKey = NotificationCapturePolicy.dedupKey(event.key, event.postTime)
        val now = clock.millis()
        val database = databaseProvider.get()
        val dao = database.pendingDraftDao()
        val keyed = buildList {
            dao.findByNotificationKey(dedupKey)?.let(::add)
            if (event.key != dedupKey) dao.findByNotificationKey(event.key)?.let(::add)
            if (event.key.isNotBlank()) addAll(dao.findBySbnKey(event.key))
        }
        val recent = dao.listCreatedSince(ledgerId, now - ImportMatcher.WINDOW_MILLIS)
        val existing = (keyed + recent).distinctBy { it.id }.map { it.toDedup() }
        val incoming = DedupIncoming(
            dedupKey = dedupKey,
            sbnKey = event.key,
            postTime = event.postTime,
            groupKey = event.groupKey,
            type = parsed.type,
            amountMinor = parsed.amountMinor,
            needsAmount = parsed.needsAmount,
            now = now,
            merchant = parsed.counterparty,
        )
        val amountForMatch = parsed.amountMinor ?: 0L
        var likelyDuplicate = false
        var enrichId: String? = null
        when (val decision = NotificationCapturePolicy.dedup(incoming, existing)) {
            DedupDecision.KeepFirst -> {
                val dropped = if (!parsed.needsAmount && parsed.amountMinor != null) {
                    dismissAmountless(existing, parsed.type, parsed.counterparty, event.postedAt, now)
                } else {
                    null
                }
                return NotificationRecordResult.Duplicate("重复通知，已保留第一次记录", cancelDraftId = dropped)
            }
            is DedupDecision.Enrich -> {
                enrichId = decision.draftId
                likelyDuplicate = decision.likelyDuplicate
            }
            is DedupDecision.Insert -> likelyDuplicate = decision.likelyDuplicate
        }
        if (!parsed.needsAmount && amountForMatch > 0L) {
            val start = event.postedAt - ImportMatcher.WINDOW_MILLIS
            val end = event.postedAt + ImportMatcher.WINDOW_MILLIS + 1
            val transactions = database.transactionDao().listFuzzyCandidates(ledgerId, start, end).map { row ->
                MatchCandidate(
                    id = row.id,
                    kind = CandidateKind.TRANSACTION,
                    type = row.type,
                    amountMinor = row.amountMinor,
                    transactionAt = row.transactionAt,
                    origin = row.origin,
                    externalSource = row.externalSource,
                    externalId = row.externalId,
                )
            }
            val hard = ImportMatcher.match(
                source = parsed.source,
                externalId = "",
                type = parsed.type,
                amountMinor = amountForMatch,
                transactionAt = event.postedAt,
                knownExternalIds = emptySet<ExternalKey>(),
                candidates = transactions,
                windowMillis = CaptureDedup.WINDOW_MILLIS,
            )
            if (hard.flag == DuplicateFlag.LIKELY_DUPLICATE && hard.hit != null) {
                val pendingId = amountlessId(existing, parsed.type, parsed.counterparty, event.postedAt, now)
                if (pendingId != null) {
                    val filled = draftRepository.completeAmount(
                        id = pendingId,
                        amountMinor = amountForMatch,
                        counterparty = parsed.counterparty,
                        rawText = event.text,
                        likelyDuplicate = true,
                    )
                    if (filled) draftRepository.markMerged(pendingId, hard.hit.candidateId)
                }
                return NotificationRecordResult.Duplicate(
                    "约三分钟内已有相同金额和方向的记录",
                    cancelDraftId = pendingId,
                )
            }
            val match = ImportMatcher.match(
                source = parsed.source,
                externalId = "",
                type = parsed.type,
                amountMinor = amountForMatch,
                transactionAt = event.postedAt,
                knownExternalIds = emptySet<ExternalKey>(),
                candidates = transactions,
            )
            if (match.flag == DuplicateFlag.LIKELY_DUPLICATE) likelyDuplicate = true
        }
        val categoryType = if (parsed.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val categories = categoryRepository.observeCategories(ledgerId, categoryType).first()
        val accounts = accountRepository.observeAccounts(ledgerId).first()
        val remembered = merchantCategories.findCategoryId(ledgerId, parsed.type, parsed.counterparty)
        val categoryId = CategoryGuess.idFor(parsed.type, parsed.counterparty, categories, remembered)
        val accountId = AccountHintMapper.suggest(
            paymentMethod = "",
            source = parsed.source,
            accounts = accounts,
            defaultAccountId = settings.importAccountId(ledgerId, parsed.source.name),
        ).accountId
        val targetId = enrichId
        if (targetId != null && parsed.amountMinor != null) {
            val filled = draftRepository.completeAmount(
                id = targetId,
                amountMinor = parsed.amountMinor,
                counterparty = parsed.counterparty,
                rawText = event.text,
                likelyDuplicate = likelyDuplicate,
            )
            if (filled) {
                val needsAssignment = categoryId == null || accountId == null
                val autoConfirmed = settings.autoConfirmDrafts && !likelyDuplicate && !needsAssignment &&
                    draftRepository.confirm(targetId) is DraftConfirmResult.Confirmed
                return NotificationRecordResult.Enriched(
                    draftId = targetId,
                    source = parsed.source,
                    type = parsed.type,
                    amountMinor = parsed.amountMinor,
                    counterparty = parsed.counterparty,
                    autoConfirmed = autoConfirmed,
                    patternId = parsed.patternId,
                )
            }
        }
        val draftId = try {
            draftRepository.insert(
                NewDraft(
                    ledgerId = ledgerId,
                    source = parsed.source,
                    type = parsed.type,
                    amountMinor = parsed.amountMinor ?: 0L,
                    counterparty = parsed.counterparty,
                    rawText = event.text,
                    categoryId = categoryId,
                    accountId = accountId,
                    transactionAt = event.postedAt,
                    notificationKey = dedupKey,
                    likelyDuplicate = likelyDuplicate,
                    needsAmount = parsed.needsAmount,
                    sbnKey = event.key,
                    groupKey = event.groupKey,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "草稿插入失败 key=${event.key}", error)
            if (error is SQLiteConstraintException || dao.findByNotificationKey(dedupKey) != null) {
                return NotificationRecordResult.Duplicate("重复通知，已保留第一次记录")
            }
            return NotificationRecordResult.Failed(error.message ?: "草稿写入失败")
        }
        val needsAssignment = categoryId == null || accountId == null
        val autoConfirmed = !parsed.needsAmount && settings.autoConfirmDrafts && !likelyDuplicate && !needsAssignment &&
            draftRepository.confirm(draftId) is DraftConfirmResult.Confirmed
        return NotificationRecordResult.Created(
            draftId = draftId,
            source = parsed.source,
            type = parsed.type,
            amountMinor = parsed.amountMinor ?: 0L,
            counterparty = parsed.counterparty,
            autoConfirmed = autoConfirmed,
            needsAssignment = needsAssignment && !autoConfirmed,
            needsAmount = parsed.needsAmount,
            patternId = parsed.patternId,
            openBill = parsed.maskedAmount,
            sourceKey = event.key,
        )
    }

    private suspend fun dismissAmountless(
        existing: List<DedupExisting>,
        type: TransactionType,
        merchant: String,
        postedAt: Long,
        now: Long,
    ): String? {
        return amountlessId(existing, type, merchant, postedAt, now)?.also { id ->
            draftRepository.dismiss(id)
        }
    }

    private fun amountlessId(
        existing: List<DedupExisting>,
        type: TransactionType,
        merchant: String,
        postedAt: Long,
        now: Long,
    ): String? {
        val candidates = existing.flatMap { row ->
            if (!row.pending || !row.needsAmount || row.type != type) {
                emptyList()
            } else {
                listOf(
                    FillCandidate(row.id, row.type, row.createdAt, merchant = row.merchant),
                    FillCandidate(row.id, row.type, row.postTime, merchant = row.merchant),
                )
            }
        }
        return CaptureDedup.fillTarget(
            direction = type,
            at = now,
            candidates = candidates,
            alsoAt = postedAt,
            merchant = merchant,
        )?.id
    }

    private fun PendingDraftEntity.toDedup(): DedupExisting {
        return DedupExisting(
            id = id,
            dedupKey = notificationKey.orEmpty(),
            sbnKey = sbnKey ?: notificationKey.orEmpty(),
            postTime = transactionAt,
            groupKey = groupKey,
            type = type,
            amountMinor = amountMinor,
            needsAmount = needsAmount,
            pending = status == com.example.localledger.domain.importing.DraftStatus.PENDING,
            createdAt = createdAt,
            merchant = counterparty.orEmpty(),
        )
    }

    companion object {
        const val WECHAT_PACKAGE = NotificationCapturePolicy.WECHAT_PACKAGE
        const val ALIPAY_PACKAGE = NotificationCapturePolicy.ALIPAY_PACKAGE
        private const val TAG = "NiuNiuNotify"
    }
}
