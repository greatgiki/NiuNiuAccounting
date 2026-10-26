package com.example.localledger.capture

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localledger.domain.capture.PaymentPageRules
import com.example.localledger.domain.capture.ScreenNode

internal class ScreenSnapshots {
    fun copy(root: AccessibilityNodeInfo): ScreenNode? {
        val budget = Budget()
        val node = read(root, depth = 0, budget) ?: return null
        if (!budget.sawMarker) return null
        return node
    }

    private fun read(node: AccessibilityNodeInfo, depth: Int, budget: Budget): ScreenNode? {
        if (budget.left <= 0 || depth > 14) return null
        if (!budget.sawMarker && budget.visited >= 64) return null
        budget.left -= 1
        budget.visited += 1
        val password = isPassword(node)
        val text = if (password) "" else safe { node.text?.toString() }.orEmpty()
        val description = if (password) "" else safe { node.contentDescription?.toString() }.orEmpty()
        if (!password) note(budget, text, description)
        val children = ArrayList<ScreenNode>(4)
        if (!password) {
            val count = safe { node.childCount } ?: 0
            for (index in 0 until count) {
                if (budget.left <= 0) break
                if (!budget.sawMarker && budget.visited >= 64) break
                val child = safe { node.getChild(index) } ?: continue
                try {
                    read(child, depth + 1, budget)?.let { children += it }
                } finally {
                    recycle(child)
                }
            }
        }
        return ScreenNode(
            text = text,
            description = description,
            viewId = if (password) "" else safe { node.viewIdResourceName }.orEmpty(),
            className = safe { node.className?.toString() }.orEmpty(),
            password = password,
            children = children,
        )
    }

    private fun note(budget: Budget, text: String, description: String) {
        if (budget.sawMarker) return
        val combined = text + description
        if (combined.isEmpty()) return
        budget.sawMarker = MARKERS.any { marker -> marker in combined }
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) {
        safe { node.recycle() }
    }

    private fun isPassword(node: AccessibilityNodeInfo): Boolean {
        if (safe { node.isPassword } == true) return true
        val className = safe { node.className?.toString() }.orEmpty().lowercase()
        val viewId = safe { node.viewIdResourceName }.orEmpty().lowercase()
        return "password" in className || "password" in viewId || "pwd" in viewId
    }

    private inline fun <T> safe(block: () -> T): T? {
        return try {
            block()
        } catch (error: Exception) {
            Log.w(TAG, "读取节点失败")
            null
        }
    }

    private class Budget(
        var left: Int = 280,
        var visited: Int = 0,
        var sawMarker: Boolean = false,
    )

    private companion object {
        const val TAG = "NiuNiuCapture"
        val MARKERS = PaymentPageRules.successMarkers + PaymentPageRules.detailMarkers + listOf("支付", "付款", "收款", "转账", "退款")
    }
}
