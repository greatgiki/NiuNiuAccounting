package com.example.localledger.capture

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.localledger.domain.capture.CapturePage
import com.example.localledger.domain.capture.CaptureSelection
import com.example.localledger.domain.capture.CaptureSheet
import com.example.localledger.domain.capture.DuplicatePlace
import com.example.localledger.domain.capture.NamedChoice
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.MinorText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class CaptureSheetController(
    private val context: Context,
    initial: CaptureSheet,
    private val onSave: (CaptureSelection) -> Unit,
    private val onLater: (CaptureSelection) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var sheet = initial
    private var type = initial.type
    private var accountId = initial.accountId
    private var toAccountId = initial.toAccountId
    private var categoryId = initial.categoryId
    private var categoryTouched = false
    private var openList: String? = null
    private val palette = CapturePalette.of(initial.night)
    private val zone = ZoneId.systemDefault()
    private val clockFormat = DateTimeFormatter.ofPattern("M月d日 HH:mm")

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBackground()
        val pad = dp(16)
        setPadding(pad, pad, pad, dp(12))
    }

    init {
        rebuild()
    }

    fun offerCategory(categoryId: String) {
        if (categoryTouched || type == TransactionType.TRANSFER) return
        if (this.categoryId == categoryId) return
        this.categoryId = categoryId
        sheet = sheet.copy(categoryId = categoryId)
        rebuild()
    }

    private fun rebuild() {
        view.removeAllViews()
        view.addView(title())
        view.addView(amount())
        if (sheet.duplicateText.isNotBlank()) view.addView(note(sheet.duplicateText, palette.warn))
        val who = sheet.merchant.ifBlank { sheet.source.label() }
        view.addView(body(who, 16f, palette.ink))
        if (sheet.accountLabel.isNotBlank()) {
            view.addView(body("页面账户  ${sheet.accountLabel}", 13f, palette.muted))
        }
        view.addView(body(clockFormat.format(Instant.ofEpochMilli(sheet.transactionAt).atZone(zone)), 13f, palette.muted))
        view.addView(typeRow())
        view.addView(choiceRow("账户", accountName(), "account"))
        if (openList == "account") view.addView(options(sheet.accounts) { accountId = it; openList = null; rebuild() })
        if (type == TransactionType.TRANSFER) {
            view.addView(choiceRow("转入", transferName(), "to"))
            if (openList == "to") {
                view.addView(options(sheet.accounts.filter { it.id != accountId }) {
                    toAccountId = it
                    openList = null
                    rebuild()
                })
            }
        } else {
            view.addView(choiceRow("分类", categoryName(), "category"))
            if (openList == "category") {
                view.addView(options(categories()) {
                    categoryId = it
                    categoryTouched = true
                    openList = null
                    rebuild()
                })
            }
        }
        if (!canSave()) view.addView(note("还缺账户或分类。可以先放进待确认。", palette.muted))
        view.addView(actions())
    }

    private fun title(): TextView {
        val page = if (sheet.page == CapturePage.DETAIL) "账单详情" else "支付成功"
        return TextView(context).apply {
            text = "${sheet.source.label()} · $page"
            setTextColor(palette.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            letterSpacing = 0.04f
        }
    }

    private fun amount(): TextView {
        val sign = when (type) {
            TransactionType.INCOME -> "+"
            TransactionType.EXPENSE -> "−"
            TransactionType.TRANSFER -> ""
        }
        return TextView(context).apply {
            text = sign + MinorText.format(sheet.amountMinor)
            setTextColor(when (type) {
                TransactionType.INCOME -> palette.income
                TransactionType.EXPENSE -> palette.expense
                TransactionType.TRANSFER -> palette.gold
            })
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            setFontFeatureSettings("tnum")
            letterSpacing = -0.03f
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun typeRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(4))
        }
        listOf(
            TransactionType.EXPENSE to "支出",
            TransactionType.INCOME to "收入",
            TransactionType.TRANSFER to "转账",
        ).forEach { (value, label) ->
            row.addView(chip(label, value == type) {
                if (type == value) return@chip
                type = value
                if (value != TransactionType.TRANSFER) {
                    categoryId = if (value == sheet.type) sheet.categoryId else null
                    categoryTouched = false
                }
                openList = null
                rebuild()
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) })
        }
        return row
    }

    private fun choiceRow(label: String, value: String, key: String): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setOnClickListener {
                openList = if (openList == key) null else key
                rebuild()
            }
        }
        row.addView(body(label, 15f, palette.muted), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(body(value, 15f, palette.ink))
        return row
    }

    private fun options(choices: List<NamedChoice>, onPick: (String) -> Unit): ScrollView {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (choices.isEmpty()) {
            column.addView(body("还没有可选项", 14f, palette.muted))
        }
        choices.forEach { choice ->
            column.addView(TextView(context).apply {
                text = choice.name
                setTextColor(palette.ink)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                minHeight = dp(44)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { onPick(choice.id) }
            })
        }
        return ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(column)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(160),
            )
        }
    }

    private fun actions(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val primary = sheet.duplicate == DuplicatePlace.TRANSACTION
        row.addView(textButton("忽略") { onDismiss() })
        row.addView(textButton("稍后") { onLater(selection()) })
        row.addView(fillButton(if (primary) "仍要记下" else "记下", enabled = canSave()) {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onSave(selection())
        })
        return row
    }

    private fun selection() = CaptureSelection(
        type = type,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        categoryTouched = categoryTouched,
    )

    private fun canSave(): Boolean {
        if (accountId.isNullOrBlank()) return false
        if (type == TransactionType.TRANSFER) {
            return !toAccountId.isNullOrBlank() && toAccountId != accountId
        }
        return !categoryId.isNullOrBlank()
    }

    private fun categories(): List<NamedChoice> {
        return if (type == TransactionType.INCOME) sheet.incomeCategories else sheet.expenseCategories
    }

    private fun accountName(): String = sheet.accounts.firstOrNull { it.id == accountId }?.name ?: "未选择"
    private fun transferName(): String = sheet.accounts.firstOrNull { it.id == toAccountId }?.name ?: "未选择"
    private fun categoryName(): String = categories().firstOrNull { it.id == categoryId }?.name ?: "未选择"

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (selected) palette.onGold else palette.ink)
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (selected) palette.gold else palette.page)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun fillButton(label: String, enabled: Boolean, onClick: (View) -> Unit): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            minHeight = dp(48)
            minWidth = dp(96)
            setPadding(dp(16), 0, dp(16), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(if (enabled) palette.onGold else palette.muted)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (enabled) palette.gold else palette.page)
            }
            isEnabled = enabled
            setOnClickListener { if (enabled) onClick(it) }
        }
    }

    private fun textButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            minHeight = dp(48)
            minWidth = dp(64)
            setPadding(dp(12), 0, dp(12), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(palette.gold)
            setOnClickListener { onClick() }
        }
    }

    private fun body(text: String, size: Float, color: Int): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun note(text: String, color: Int): TextView {
        return body(text, 13f, color).apply { setPadding(0, dp(6), 0, dp(2)) }
    }

    private fun cardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(palette.surface)
            setStroke(dp(1).coerceAtLeast(1), palette.hairline)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

internal data class CapturePalette(
    val surface: Int,
    val ink: Int,
    val muted: Int,
    val gold: Int,
    val onGold: Int,
    val hairline: Int,
    val expense: Int,
    val income: Int,
    val warn: Int,
    val page: Int,
) {
    companion object {
        fun of(night: Boolean): CapturePalette = if (night) DARK else LIGHT

        private val LIGHT = CapturePalette(
            surface = 0xFFFBF8F3.toInt(),
            ink = 0xFF1C1915.toInt(),
            muted = 0xFF5C564C.toInt(),
            gold = 0xFF6E5128.toInt(),
            onGold = 0xFFFFF9F2.toInt(),
            hairline = 0xFFD9D0C3.toInt(),
            expense = 0xFF8E3D32.toInt(),
            income = 0xFF2F6B52.toInt(),
            warn = 0xFF8E3D32.toInt(),
            page = 0xFFF4EFE6.toInt(),
        )

        private val DARK = CapturePalette(
            surface = 0xFF171A1F.toInt(),
            ink = 0xFFEDE6DA.toInt(),
            muted = 0xFFC4BBAE.toInt(),
            gold = 0xFFD4B483.toInt(),
            onGold = 0xFF2A2114.toInt(),
            hairline = 0xFF3A3F47.toInt(),
            expense = 0xFFE0A598.toInt(),
            income = 0xFF8FBFAB.toInt(),
            warn = 0xFFE0A598.toInt(),
            page = 0xFF101216.toInt(),
        )
    }
}
