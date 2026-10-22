package com.example.localledger.domain.voice

import com.example.localledger.domain.importing.CategoryKeywordMapper
import com.example.localledger.domain.importing.MerchantKey
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class VoiceCategoryRef(
    val id: String,
    val name: String,
    val type: CategoryType,
)

data class VoiceAccountRef(
    val id: String,
    val name: String,
    val type: AccountType,
)

data class VoiceLedgerDraft(
    val type: TransactionType,
    val amount: BigDecimal?,
    val categoryId: String?,
    val categoryName: String?,
    val accountId: String?,
    val accountName: String?,
    val date: LocalDate,
    val note: String,
    val sourceText: String,
    /** Spoken merchant whose user override matched, when one did. */
    val merchant: String? = null,
)

/**
 * A row from merchant_category_rules. [merchantKey] is [MerchantKey.normalize]'d.
 * An override whose category is missing or archived is ignored.
 */
data class VoiceMerchantOverride(
    val merchantKey: String,
    val categoryId: String,
    val type: TransactionType,
)

/**
 * Rule-based parser for spoken bookkeeping. No network and no model:
 * amounts, income words, relative dates, the user's categories, account names,
 * and merchant_category_rules overrides.
 * Several items in one utterance are split on 和 / 还有 / ，.
 * A user override beats a keyword of the same length. A category name of that length, or longer, still wins.
 */
object VoiceLedgerParser {
    private val incomeWords = listOf("收入", "工资", "报销", "退款", "红包")
    private val expenseHongbao = Regex("""发(了|个|出)?红包|包红包|给(了)?红包|塞(了)?红包""")
    private val strongSeparator = Regex("""还有|以及|，|、|；|;|(?<!\d),(?!\d)""")
    private val fillers = listOf(
        "人民币", "块钱", "花了", "付了", "支付了", "买了", "花费", "用了",
        "收到", "到账", "入账", "一共", "总共", "帮我记", "帮我", "记一下", "记一笔",
    )
    private val weekday = mapOf(
        '一' to DayOfWeek.MONDAY,
        '二' to DayOfWeek.TUESDAY,
        '三' to DayOfWeek.WEDNESDAY,
        '四' to DayOfWeek.THURSDAY,
        '五' to DayOfWeek.FRIDAY,
        '六' to DayOfWeek.SATURDAY,
        '日' to DayOfWeek.SUNDAY,
        '天' to DayOfWeek.SUNDAY,
    )
    private val relativeWords = listOf(
        "大前天" to 3L,
        "前天" to 2L,
        "昨天" to 1L,
        "昨日" to 1L,
        "今天" to 0L,
        "今日" to 0L,
    )
    private val weekPattern = Regex("""上周[一二三四五六日天]|本周[一二三四五六日天]|星期[一二三四五六日天]|周[一二三四五六日天]""")
    private val monthDayPattern = Regex(
        """(\d{1,2}|[零〇一二两三四五六七八九十]{1,4})月(\d{1,2}|[零〇一二两三四五六七八九十]{1,4})[日号]""",
    )
    private val dayOnlyPattern = Regex(
        """(?<![月\d零〇一二两三四五六七八九十])(\d{1,2}|[零〇一二两三四五六七八九十]{1,3})[日号]""",
    )

    private val spokenExpense = mapOf(
        "餐饮" to listOf("午饭", "晚饭", "早饭", "早餐", "午餐", "晚餐", "外卖", "吃饭", "食堂", "夜宵", "宵夜", "下午茶", "奶茶", "咖啡", "买菜"),
        "交通" to listOf("打车", "打的", "地铁", "公交", "出租", "滴滴", "的士", "高铁", "火车", "机票", "加油", "停车", "单车", "共享单车"),
        "购物" to listOf("超市", "便利店", "淘宝", "京东", "拼多多"),
        "居住" to listOf("房租", "水电", "物业", "燃气", "电费", "水费"),
        "娱乐" to listOf("电影", "游戏"),
        "医疗" to listOf("医院", "药店", "看病", "药费"),
        "教育" to listOf("学费", "培训", "课程"),
        "通讯" to listOf("话费", "流量"),
    )
    private val spokenIncome = mapOf(
        "工资" to listOf("工资", "薪资", "薪水", "代发"),
        "奖金" to listOf("奖金", "年终奖"),
        "兼职" to listOf("兼职", "稿费"),
        "退款" to listOf("退款"),
    )

    fun parse(
        utterance: String,
        categories: List<VoiceCategoryRef>,
        accounts: List<VoiceAccountRef>,
        today: LocalDate,
        merchantOverrides: List<VoiceMerchantOverride> = emptyList(),
    ): List<VoiceLedgerDraft> {
        val text = normalizeUtterance(utterance)
        if (text.isEmpty()) return emptyList()
        val pieces = splitParts(text, categories, merchantOverrides)
            .map { parsePiece(it, categories, accounts, today, merchantOverrides) }
        val items = pieces.filter { it.item }
        if (items.isEmpty()) {
            val only = parsePiece(text, categories, accounts, today, merchantOverrides)
            return listOf(only.toDraft(date = only.date ?: today, account = only.account, notePrefix = ""))
        }
        val distinctDates = pieces.mapNotNull { it.date }.distinct()
        var runningDate = distinctDates.singleOrNull()
            ?: pieces.firstOrNull { !it.item && it.date != null }?.date
            ?: today
        val mentionedAccounts = pieces.mapNotNull { it.account }.distinctBy { it.id }
        var runningAccount = mentionedAccounts.singleOrNull()
            ?: pieces.firstOrNull { !it.item && it.account != null }?.account
        val notePrefix = pieces.filter { !it.item }.map { it.note }.filter { it.isNotBlank() }.joinToString(" ")
        return items.map { item ->
            val date = item.date ?: runningDate
            if (item.date != null) runningDate = item.date
            val account = item.account ?: runningAccount
            if (item.account != null) runningAccount = item.account
            item.toDraft(date, account, notePrefix)
        }
    }

    private data class Piece(
        val item: Boolean,
        val type: TransactionType,
        val amount: BigDecimal?,
        val categoryId: String?,
        val categoryName: String?,
        val account: VoiceAccountRef?,
        val date: LocalDate?,
        val note: String,
        val source: String,
        val merchant: String?,
    )

    private fun Piece.toDraft(
        date: LocalDate,
        account: VoiceAccountRef?,
        notePrefix: String,
    ): VoiceLedgerDraft {
        val chosen = this.account ?: account
        val note = listOf(notePrefix, this.note).filter { it.isNotBlank() }.joinToString(" ")
        return VoiceLedgerDraft(
            type = type,
            amount = amount,
            categoryId = categoryId,
            categoryName = categoryName,
            accountId = chosen?.id,
            accountName = chosen?.name,
            date = date,
            note = note,
            sourceText = source,
            merchant = merchant,
        )
    }

    private fun parsePiece(
        source: String,
        categories: List<VoiceCategoryRef>,
        accounts: List<VoiceAccountRef>,
        today: LocalDate,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): Piece {
        val type = detectType(source, categories, merchantOverrides)
        val dateHit = findDate(source, today)
        var working = blank(source, dateHit?.range)
        val amountHit = ChineseAmounts.find(working)
        if (amountHit != null) working = blank(working, amountHit.start until amountHit.endExclusive)
        val accountHit = findAccount(working, accounts)
        if (accountHit != null) working = blank(working, accountHit.range)
        val category = resolveCategory(working, type, categories, merchantOverrides)
        category?.keywords?.forEach { keyword ->
            rangesOf(working, keyword).forEach { working = blank(working, it) }
        }
        working = working.replace("收入", " ").replace("支出", " ")
        val note = cleanupNote(working)
        val categoryRef = category?.category
        val spokenSignal = hasExpenseSignal(source, categories, merchantOverrides) ||
            hasIncomeSignal(source, categories, merchantOverrides) ||
            category != null
        return Piece(
            item = amountHit != null || spokenSignal,
            type = type,
            amount = amountHit?.amount,
            categoryId = categoryRef?.id,
            categoryName = category?.targetName,
            account = accountHit?.account,
            date = dateHit?.date,
            note = note,
            source = source,
            merchant = category?.merchant,
        )
    }

    private fun splitParts(
        text: String,
        categories: List<VoiceCategoryRef>,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): List<String> {
        return strongSeparator.split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { splitHe(it, categories, merchantOverrides) }
    }

    private fun splitHe(
        text: String,
        categories: List<VoiceCategoryRef>,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): List<String> {
        if (!text.contains('和')) return listOf(text)
        val pieces = text.split('和').map { it.trim() }.filter { it.isNotEmpty() }
        if (pieces.size > 1 && pieces.all { looksLikeItem(it, categories, merchantOverrides) }) return pieces
        return listOf(text)
    }

    private fun looksLikeItem(
        text: String,
        categories: List<VoiceCategoryRef>,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): Boolean {
        if (ChineseAmounts.find(text) != null) return true
        if (hasIncomeSignal(text, categories, merchantOverrides) || hasExpenseSignal(text, categories, merchantOverrides)) return true
        return categories.any { it.name.length >= 2 && it.name != "其他" && text.contains(it.name) }
    }

    private fun detectType(
        text: String,
        categories: List<VoiceCategoryRef>,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): TransactionType {
        val masked = expenseHongbao.replace(text, "")
        if (incomeWords.any { masked.contains(it) }) return TransactionType.INCOME
        val expense = keywordScore(text, TransactionType.EXPENSE, categories, merchantOverrides)
        val income = keywordScore(text, TransactionType.INCOME, categories, merchantOverrides)
        return if (income > expense && income > 0) TransactionType.INCOME else TransactionType.EXPENSE
    }

    private fun keywordScore(
        text: String,
        type: TransactionType,
        categories: List<VoiceCategoryRef>,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): Int {
        val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val name = categories
            .filter { it.type == categoryType && it.name.length >= 2 && it.name != "其他" && text.contains(it.name) }
            .maxOfOrNull { it.name.length } ?: 0
        val spoken = spokenWords(type).filter { text.contains(it) }.maxOfOrNull { it.length } ?: 0
        val mapped = CategoryKeywordMapper.match(type, text)?.keyword?.length ?: 0
        val remembered = matchingOverrides(text, type, categories, merchantOverrides)
            .maxOfOrNull { it.second.last - it.second.first + 1 } ?: 0
        return maxOf(name, spoken, mapped, remembered)
    }

    private data class CategoryResolution(
        val category: VoiceCategoryRef?,
        val targetName: String,
        val keywords: List<String>,
        val merchant: String?,
    )

    private data class Candidate(
        val length: Int,
        val priority: Int,
        val keyword: String,
        val targetName: String,
        val merchant: String? = null,
    )

    private fun resolveCategory(
        text: String,
        type: TransactionType,
        categories: List<VoiceCategoryRef>,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): CategoryResolution? {
        val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val pool = categories.filter { it.type == categoryType && it.name.isNotBlank() }
        val hits = ArrayList<Candidate>()
        for (category in pool) {
            if (category.name.length >= 2 && category.name != "其他" && text.contains(category.name)) {
                hits += Candidate(category.name.length, priority = 3, category.name, category.name)
            }
        }
        val spoken = if (type == TransactionType.INCOME) spokenIncome else spokenExpense
        for ((name, words) in spoken) {
            for (word in words) {
                if (text.contains(word)) hits += Candidate(word.length, priority = 1, word, name)
            }
        }
        val mapped = CategoryKeywordMapper.match(type, text)
        if (mapped != null) {
            hits += Candidate(mapped.keyword.length, priority = 1, mapped.keyword, mapped.categoryName)
        }
        for ((rule, span) in matchingOverrides(text, type, categories, merchantOverrides)) {
            val category = pool.firstOrNull { it.id == rule.categoryId } ?: continue
            val spoken = text.substring(span)
            hits += Candidate(spoken.length, priority = 2, spoken, category.name, merchant = spoken)
        }
        if (hits.isEmpty()) {
            val other = pool.firstOrNull { it.name == "其他" && text.contains(it.name) } ?: return null
            return CategoryResolution(other, other.name, listOf(other.name), merchant = null)
        }
        val bestLength = hits.maxOf { it.length }
        val best = hits.filter { it.length == bestLength }.maxBy { it.priority }
        val keywords = hits.filter { it.targetName == best.targetName }.map { it.keyword }.distinct()
        val merchants = hits.mapNotNull { it.merchant }
        val merchant = merchants.maxByOrNull { it.length }
        val category = pool.firstOrNull { it.name == best.targetName }
            ?: pool.firstOrNull { it.name == best.keyword }
        return CategoryResolution(category, best.targetName, (keywords + merchants).distinct(), merchant)
    }

    private fun matchingOverrides(
        text: String,
        type: TransactionType,
        categories: List<VoiceCategoryRef>,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): List<Pair<VoiceMerchantOverride, IntRange>> {
        if (merchantOverrides.isEmpty() || type == TransactionType.TRANSFER) return emptyList()
        val ids = activeIds(categories, type)
        if (ids.isEmpty()) return emptyList()
        return merchantOverrides.mapNotNull { rule ->
            if (rule.type != type || rule.categoryId !in ids) return@mapNotNull null
            val span = merchantSpan(text, rule.merchantKey) ?: return@mapNotNull null
            rule to span
        }
    }

    private fun activeIds(categories: List<VoiceCategoryRef>, type: TransactionType): Set<String> {
        val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        return categories.filter { it.type == categoryType }.map { it.id }.toSet()
    }

    private fun merchantSpan(text: String, key: String): IntRange? {
        val compact = MerchantKey.normalize(key).replace(" ", "")
        if (compact.length < 2) return null
        val index = text.lowercase(Locale.ROOT).indexOf(compact)
        if (index < 0) return null
        return index until index + compact.length
    }

    private data class AccountHit(val account: VoiceAccountRef, val range: IntRange)

    private fun findAccount(text: String, accounts: List<VoiceAccountRef>): AccountHit? {
        val named = accounts
            .filter { it.name.length >= 2 && text.contains(it.name) }
            .maxByOrNull { it.name.length }
        if (named != null) {
            val index = text.indexOf(named.name)
            return AccountHit(named, index until index + named.name.length)
        }
        data class Alias(val keys: List<String>, val pick: (List<VoiceAccountRef>) -> VoiceAccountRef?)
        val aliases = listOf(
            Alias(listOf("微信支付", "微信", "零钱")) { unique(it) { account -> account.name.contains("微信") } },
            Alias(listOf("支付宝", "余额宝")) { unique(it) { account -> account.name.contains("支付宝") } },
            Alias(listOf("花呗")) {
                unique(it) { account -> account.name.contains("花呗") }
                    ?: unique(it) { account -> account.name.contains("支付宝") }
            },
            Alias(listOf("信用卡")) {
                unique(it) { account -> account.type == AccountType.CREDIT_CARD || account.name.contains("信用卡") }
            },
            Alias(listOf("银行卡", "储蓄卡", "借记卡")) {
                unique(it) { account -> account.type == AccountType.BANK_CARD || account.name.contains("银行卡") }
            },
            Alias(listOf("现金")) {
                unique(it) { account -> account.type == AccountType.CASH || account.name.contains("现金") }
            },
        )
        var bestKey: String? = null
        var bestAccount: VoiceAccountRef? = null
        for (alias in aliases) {
            val key = alias.keys.filter { text.contains(it) }.maxByOrNull { it.length } ?: continue
            val account = alias.pick(accounts) ?: continue
            if (bestKey == null || key.length > bestKey.length) {
                bestKey = key
                bestAccount = account
            }
        }
        if (bestKey != null && bestAccount != null) {
            val index = text.indexOf(bestKey)
            return AccountHit(bestAccount, index until index + bestKey.length)
        }
        if (Regex("卡(?!车)").containsMatchIn(text)) {
            val cards = accounts.filter {
                it.type == AccountType.BANK_CARD || it.type == AccountType.CREDIT_CARD || it.name.contains("卡")
            }
            val only = cards.singleOrNull() ?: return null
            val index = text.indexOf('卡')
            if (index < 0) return null
            return AccountHit(only, index..index)
        }
        return null
    }

    private fun unique(
        accounts: List<VoiceAccountRef>,
        predicate: (VoiceAccountRef) -> Boolean,
    ): VoiceAccountRef? = accounts.filter(predicate).singleOrNull()

    private data class DateHit(val date: LocalDate, val range: IntRange)

    private fun findDate(text: String, today: LocalDate): DateHit? {
        val hits = listOfNotNull(findRelative(text, today), findWeek(text, today), findMonthDay(text, today), findDayOnly(text, today))
        return hits.minWithOrNull(compareBy<DateHit> { it.range.first }.thenByDescending { it.range.last - it.range.first })
    }

    private fun findRelative(text: String, today: LocalDate): DateHit? {
        var best: DateHit? = null
        for ((word, days) in relativeWords) {
            val index = text.indexOf(word)
            if (index < 0) continue
            if (best != null && index >= best.range.first) continue
            best = DateHit(today.minusDays(days), index until index + word.length)
        }
        return best
    }

    private fun findWeek(text: String, today: LocalDate): DateHit? {
        val match = weekPattern.find(text) ?: return null
        val token = match.value
        val day = weekday[token.last()] ?: return null
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val date = when {
            token.startsWith("上周") -> thisMonday.minusWeeks(1).with(day)
            token.startsWith("本周") || token.startsWith("星期") -> thisMonday.with(day)
            else -> {
                val candidate = thisMonday.with(day)
                if (candidate.isAfter(today)) candidate.minusWeeks(1) else candidate
            }
        }
        return DateHit(date, match.range)
    }

    private fun findMonthDay(text: String, today: LocalDate): DateHit? {
        val match = monthDayPattern.find(text) ?: return null
        val month = parseSmallNumber(match.groupValues[1]) ?: return null
        val day = parseSmallNumber(match.groupValues[2]) ?: return null
        val resolved = resolveMonthDay(month, day, today) ?: return null
        return DateHit(resolved, match.range)
    }

    private fun findDayOnly(text: String, today: LocalDate): DateHit? {
        val match = dayOnlyPattern.find(text) ?: return null
        val day = parseSmallNumber(match.groupValues[1]) ?: return null
        if (day !in 1..31) return null
        val thisMonth = safeDate(today.year, today.monthValue, day)
        val resolved = if (thisMonth != null && !thisMonth.isAfter(today)) {
            thisMonth
        } else {
            val previous = today.minusMonths(1)
            safeDate(previous.year, previous.monthValue, day)
        } ?: return null
        return DateHit(resolved, match.range)
    }

    private fun resolveMonthDay(month: Int, day: Int, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val current = safeDate(today.year, month, day) ?: return null
        return if (current.isAfter(today)) safeDate(today.year - 1, month, day) else current
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }

    private fun parseSmallNumber(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        val parsed = ChineseAmounts.find(token) ?: return null
        if (parsed.start != 0 || parsed.endExclusive != token.length) return null
        val value = parsed.amount.stripTrailingZeros()
        if (value.scale() > 0) return null
        return value.toInt()
    }

    private fun hasIncomeSignal(
        text: String,
        categories: List<VoiceCategoryRef>,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): Boolean {
        val masked = expenseHongbao.replace(text, "")
        if (incomeWords.any { masked.contains(it) }) return true
        if (spokenIncome.values.any { words -> words.any { text.contains(it) } }) return true
        return matchingOverrides(text, TransactionType.INCOME, categories, merchantOverrides).isNotEmpty()
    }

    private fun hasExpenseSignal(
        text: String,
        categories: List<VoiceCategoryRef>,
        merchantOverrides: List<VoiceMerchantOverride>,
    ): Boolean {
        if (spokenExpense.values.any { words -> words.any { text.contains(it) } }) return true
        if (CategoryKeywordMapper.match(TransactionType.EXPENSE, text) != null) return true
        return matchingOverrides(text, TransactionType.EXPENSE, categories, merchantOverrides).isNotEmpty()
    }

    private fun spokenWords(type: TransactionType): List<String> {
        val map = if (type == TransactionType.INCOME) spokenIncome else spokenExpense
        return map.values.flatten()
    }

    private fun cleanupNote(raw: String): String {
        var text = raw
        for (filler in fillers) text = text.replace(filler, " ")
        text = text.replace(Regex("""[\s,，。．、；;：:！!？?（）()【】\[\]"'“”‘’]+"""), " ").trim()
        text = text.replace(Regex("""^[的了用把给从到啊呀呢吧嘛哦]+"""), "")
        text = text.replace(Regex("""[的了用把给啊呀呢吧嘛哦]+$"""), "")
        return text.trim()
    }

    private fun blank(text: String, range: IntRange?): String {
        if (range == null || range.isEmpty()) return text
        val chars = text.toCharArray()
        for (index in range) {
            if (index in chars.indices) chars[index] = ' '
        }
        return String(chars)
    }

    private fun rangesOf(text: String, keyword: String): List<IntRange> {
        if (keyword.isEmpty()) return emptyList()
        val ranges = ArrayList<IntRange>()
        var from = 0
        while (from < text.length) {
            val index = text.indexOf(keyword, from)
            if (index < 0) break
            ranges += index until index + keyword.length
            from = index + keyword.length
        }
        return ranges
    }
}

internal fun normalizeUtterance(raw: String): String {
    val builder = StringBuilder(raw.length)
    for (ch in raw.trim()) {
        when (ch) {
            in '０'..'９' -> builder.append('0' + (ch - '０'))
            '．' -> builder.append('.')
            '。' -> builder.append('，')
            ' ', '　', '\n', '\r', '\t' -> Unit
            else -> builder.append(ch)
        }
    }
    return builder.toString()
}
