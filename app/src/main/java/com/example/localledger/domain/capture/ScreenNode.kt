package com.example.localledger.domain.capture

import com.example.localledger.domain.importing.BillSource

/**
 * A trimmed copy of an accessibility node. Password nodes keep [password] set
 * and must not carry the field's text. Parsers only see this tree, never a
 * live [android.view.accessibility.AccessibilityNodeInfo].
 */
data class ScreenNode(
    val text: String = "",
    val description: String = "",
    val viewId: String = "",
    val className: String = "",
    val password: Boolean = false,
    val children: List<ScreenNode> = emptyList(),
)

object PaymentPackages {
    const val WECHAT = "com.tencent.mm"
    const val ALIPAY = "com.eg.android.AlipayGphone"
    const val UNIONPAY = "com.unionpay"
    const val UNIONPAY_LEGACY = "com.unionpay.uppay"

    val ALL = arrayOf(WECHAT, ALIPAY, UNIONPAY, UNIONPAY_LEGACY)

    fun sourceOf(packageName: String): BillSource? = when (packageName) {
        WECHAT -> BillSource.WECHAT
        ALIPAY -> BillSource.ALIPAY
        UNIONPAY, UNIONPAY_LEGACY -> BillSource.UNIONPAY
        else -> null
    }
}

object BankPackages {
    val APPS = setOf(
        "com.icbc",
        "com.icbc.im",
        "com.chinamworld.main",
        "com.android.bankabc",
        "com.chinamworld.bocmbci",
        "cmb.pb",
        "com.bankcomm.Bankcomm",
        "com.yitong.mbank.psbc",
        "com.ecitic.bank.mobile",
        "com.cebbank.mobile.cemb",
        "cn.com.cmbc.newmbank",
        "com.cib.cibmb",
        "com.pingan.paces.ccms",
        "cn.com.spdb.mobilebank.per",
        "com.cgbchina.xpt",
        "com.hxb.mobile.client",
        "com.bankofbeijing.mobilebanking",
        "cn.com.shbank.mper",
    )

    fun isBankApp(packageName: String?): Boolean = packageName != null && packageName in APPS
}

object SmsPackages {
    val ALL = setOf(
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.messaging",
        "com.miui.sms",
        "com.miui.mms",
        "com.coloros.mms",
        "com.hihonor.mms",
        "com.oneplus.mms",
    )

    fun isSms(packageName: String?): Boolean = packageName != null && packageName in ALL
}

/**
 * Indented text fixtures. Two spaces is one level. A line `pwd:secret` is a
 * password node: its text and children are ignored by [ScreenText].
 */
object ScreenDump {
    fun parse(dump: String): ScreenNode {
        val roots = mutableListOf<Builder>()
        val stack = ArrayDeque<Builder>()
        for (raw in dump.replace("\t", "  ").lines()) {
            if (raw.isBlank()) continue
            val indent = raw.takeWhile { it == ' ' }.length / 2
            val content = raw.trim()
            val password = content.startsWith("pwd:")
            val text = if (password) content.removePrefix("pwd:") else content
            val node = Builder(indent, text, password)
            while (stack.isNotEmpty() && stack.last().indent >= indent) stack.removeLast()
            if (stack.isEmpty()) roots += node else stack.last().kids += node
            stack.addLast(node)
        }
        return ScreenNode(children = roots.map { it.freeze() })
    }

    private class Builder(
        val indent: Int,
        val text: String,
        val password: Boolean,
        val kids: MutableList<Builder> = mutableListOf(),
    ) {
        fun freeze(): ScreenNode = ScreenNode(
            text = text,
            password = password,
            children = kids.map { it.freeze() },
        )
    }
}

object ScreenText {
    const val MAX_LINES = 80

    fun lines(root: ScreenNode): List<String> {
        val out = ArrayList<String>(24)
        walk(root, out)
        return out
    }

    private fun walk(node: ScreenNode, out: MutableList<String>) {
        if (out.size >= MAX_LINES) return
        if (node.password || looksSecret(node)) return
        add(out, node.text)
        if (node.description.isNotBlank() && node.description.trim() != node.text.trim()) {
            add(out, node.description)
        }
        for (child in node.children) {
            if (out.size >= MAX_LINES) return
            walk(child, out)
        }
    }

    private fun looksSecret(node: ScreenNode): Boolean {
        val id = node.viewId.lowercase()
        val className = node.className.lowercase()
        return "password" in id || "pwd" in id || "password" in className
    }

    private fun add(out: MutableList<String>, raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || out.size >= MAX_LINES) return
        if (out.lastOrNull() == text) return
        out += text
    }
}
