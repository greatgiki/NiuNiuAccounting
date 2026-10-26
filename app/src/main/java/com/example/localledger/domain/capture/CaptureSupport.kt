package com.example.localledger.domain.capture

import com.example.localledger.core.storage.AppSettings
import com.example.localledger.domain.ai.AiChatRequest
import com.example.localledger.domain.ai.AiMessage
import com.example.localledger.domain.ai.AiRole
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.importing.MerchantKey
import com.example.localledger.domain.model.TransactionType

data class CaptureFlags(
    val master: Boolean = false,
    val wechat: Boolean = true,
    val alipay: Boolean = true,
    val unionPay: Boolean = true,
    val bankSms: Boolean = false,
    val loaded: Boolean = false,
) {
    fun allows(packageName: String): Boolean {
        if (!master) return false
        return when (packageName) {
            PaymentPackages.WECHAT -> wechat
            PaymentPackages.ALIPAY -> alipay
            PaymentPackages.UNIONPAY, PaymentPackages.UNIONPAY_LEGACY -> unionPay
            else -> false
        }
    }

    companion object {
        fun from(settings: AppSettings?): CaptureFlags {
            if (settings == null) return CaptureFlags()
            return CaptureFlags(
                master = settings.autoRecordEnabled,
                wechat = settings.wechatNotificationEnabled,
                alipay = settings.alipayNotificationEnabled,
                unionPay = settings.unionPayEnabled,
                bankSms = settings.bankSmsEnabled,
                loaded = true,
            )
        }
    }
}

/** HyperOS 支付保护 hides overlays on the payment page itself. */
object OverlayDeferral {
    fun whilePaymentAppIsOpen(manufacturer: String, brand: String): Boolean {
        val name = "${manufacturer.lowercase()} ${brand.lowercase()}"
        return listOf("xiaomi", "redmi", "poco", "blackshark").any { token -> token in name }
    }
}

object LearnedAccount {
    const val LIMIT = 400

    fun key(ledgerId: String, type: TransactionType, merchant: String): String {
        val merchantKey = MerchantKey.normalize(merchant)
        if (ledgerId.isBlank() || merchantKey.isEmpty() || type == TransactionType.TRANSFER) return ""
        return "$ledgerId|${type.name}|$merchantKey"
    }

    fun remember(current: Map<String, String>, key: String, accountId: String): Map<String, String> {
        if (key.isEmpty() || accountId.isBlank()) return current
        val next = LinkedHashMap<String, String>(current.size + 1)
        current.forEach { (existing, value) ->
            if (existing != key) next[existing] = value
        }
        next[key] = accountId
        while (next.size > LIMIT) {
            val eldest = next.keys.firstOrNull() ?: break
            next.remove(eldest)
        }
        return next
    }
}

data class NamedChoice(val id: String, val name: String)

enum class DuplicatePlace { NONE, DRAFT, TRANSACTION }

data class CaptureSheet(
    val token: String,
    val source: BillSource,
    val page: CapturePage,
    val type: TransactionType,
    val amountMinor: Long,
    val merchant: String,
    val accountLabel: String,
    val accountId: String?,
    val toAccountId: String?,
    val categoryId: String?,
    val suggestedCategoryId: String?,
    val transactionAt: Long,
    val ledgerId: String,
    val ruleId: String,
    val duplicate: DuplicatePlace,
    val duplicateDraftId: String?,
    val duplicateText: String,
    val accounts: List<NamedChoice>,
    val expenseCategories: List<NamedChoice>,
    val incomeCategories: List<NamedChoice>,
    val aiEligible: Boolean,
    val night: Boolean = false,
)

data class CaptureSelection(
    val type: TransactionType,
    val accountId: String?,
    val toAccountId: String?,
    val categoryId: String?,
    val categoryTouched: Boolean,
)

/**
 * Category-only prompt. The merchant name and the user's category list are
 * the whole request. Screen text is never included.
 */
object CategoryHintPrompt {
    fun request(merchant: String, type: TransactionType, names: List<String>): AiChatRequest {
        val kind = when (type) {
            TransactionType.INCOME -> "收入"
            TransactionType.EXPENSE -> "支出"
            TransactionType.TRANSFER -> "转账"
        }
        return AiChatRequest(
            messages = listOf(
                AiMessage(
                    role = AiRole.SYSTEM,
                    text = "你是记账分类助手。只回复一个分类名称，必须来自用户给出的列表。不要解释。都不合适时回复：其他",
                ),
                AiMessage(
                    role = AiRole.USER,
                    text = "类型：$kind\n商户：$merchant\n可选分类：${names.joinToString("、")}",
                ),
            ),
            maxTokens = 32,
        )
    }

    fun parse(reply: String, names: List<String>): String? {
        val cleaned = reply
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()
            .trim('"', '\'', '。', '.', '，', ',', ' ', '：', ':')
        if (cleaned.isEmpty()) return null
        return names.firstOrNull { name -> name == cleaned }
    }
}
