package com.example.localledger.data.repository

import com.example.localledger.core.storage.SettingsStore
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.domain.importing.AccountHintMapper
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.importing.CandidateKind
import com.example.localledger.domain.importing.CategoryKeywordMapper
import com.example.localledger.domain.importing.DuplicateFlag
import com.example.localledger.domain.importing.ExternalKey
import com.example.localledger.domain.importing.ImportMatcher
import com.example.localledger.domain.importing.MatchCandidate
import com.example.localledger.domain.importing.PaymentNotificationParser
import com.example.localledger.domain.importing.RecordOrigin
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class CapturedNotification(
    val packageName: String,
    val text: String,
    val postedAt: Long,
    val key: String,
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
    ) : NotificationRecordResult

    data object Ignored : NotificationRecordResult
    data object Disabled : NotificationRecordResult
    data object Duplicate : NotificationRecordResult
}

@Singleton
class PaymentDraftWriter @Inject constructor(
    private val settingsStore: SettingsStore,
    private val databaseProvider: WalletDatabaseSource,
    private val draftRepository: DraftRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) {
    suspend fun record(event: CapturedNotification): NotificationRecordResult {
        val source = sourceOf(event.packageName) ?: return NotificationRecordResult.Ignored
        val settings = settingsStore.peek() ?: return NotificationRecordResult.Disabled
        if (!settings.autoRecordEnabled) return NotificationRecordResult.Disabled
        if (source == BillSource.WECHAT && !settings.wechatNotificationEnabled) return NotificationRecordResult.Disabled
        if (source == BillSource.ALIPAY && !settings.alipayNotificationEnabled) return NotificationRecordResult.Disabled
        val parsed = PaymentNotificationParser.parse(source, event.text) ?: return NotificationRecordResult.Ignored
        val ledgerId = settings.currentLedgerId
        if (event.key.isNotBlank() && draftRepository.findByNotificationKey(event.key) != null) {
            return NotificationRecordResult.Duplicate
        }
        val database = databaseProvider.get()
        val start = event.postedAt - ImportMatcher.WINDOW_MILLIS
        val end = event.postedAt + ImportMatcher.WINDOW_MILLIS + 1
        val pending = database.pendingDraftDao().listPendingInRange(ledgerId, start, end)
        if (pending.any { it.type == parsed.type && it.amountMinor == parsed.amountMinor }) {
            return NotificationRecordResult.Duplicate
        }
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
        val match = ImportMatcher.match(
            source = source,
            externalId = "",
            type = parsed.type,
            amountMinor = parsed.amountMinor,
            transactionAt = event.postedAt,
            knownExternalIds = emptySet<ExternalKey>(),
            candidates = transactions,
        )
        val likelyDuplicate = match.flag == DuplicateFlag.LIKELY_DUPLICATE
        val categoryType = if (parsed.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val categories = categoryRepository.observeCategories(ledgerId, categoryType).first()
        val accounts = accountRepository.observeAccounts(ledgerId).first()
        val categoryName = CategoryKeywordMapper.suggestName(parsed.type, parsed.counterparty)
        val categoryId = categories.firstOrNull { !it.isArchived && it.name == categoryName }?.id
            ?: categories.firstOrNull { !it.isArchived && it.name == "其他" }?.id
        val accountId = AccountHintMapper.suggest(
            paymentMethod = "",
            source = source,
            accounts = accounts,
            defaultAccountId = settings.importAccountId(ledgerId, source.name),
        ).accountId
        val draftId = try {
            draftRepository.insert(
                NewDraft(
                    ledgerId = ledgerId,
                    source = source,
                    type = parsed.type,
                    amountMinor = parsed.amountMinor,
                    counterparty = parsed.counterparty,
                    rawText = event.text,
                    categoryId = categoryId,
                    accountId = accountId,
                    transactionAt = event.postedAt,
                    notificationKey = event.key.ifBlank { null },
                    likelyDuplicate = likelyDuplicate,
                ),
            )
        } catch (_: Exception) {
            return NotificationRecordResult.Duplicate
        }
        val needsAssignment = categoryId == null || accountId == null
        val autoConfirmed = settings.autoConfirmDrafts && !likelyDuplicate && !needsAssignment &&
            draftRepository.confirm(draftId) is DraftConfirmResult.Confirmed
        return NotificationRecordResult.Created(
            draftId = draftId,
            source = source,
            type = parsed.type,
            amountMinor = parsed.amountMinor,
            counterparty = parsed.counterparty,
            autoConfirmed = autoConfirmed,
            needsAssignment = needsAssignment && !autoConfirmed,
        )
    }

    private fun sourceOf(packageName: String): BillSource? = when (packageName) {
        WECHAT_PACKAGE -> BillSource.WECHAT
        ALIPAY_PACKAGE -> BillSource.ALIPAY
        else -> null
    }

    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"
    }
}
