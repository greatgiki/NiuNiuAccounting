package com.example.localledger.domain.capture

/**
 * Words the payment-page parser looks for. When WeChat, Alipay, or UnionPay
 * rename a label, update the list here and add a fixture in
 * `PaymentScreenParserTest`. The walker does not depend on view ids.
 *
 * Mainland simplified-Chinese pages only. A page is ignored unless it matches
 * a success phrase or a bill-detail title, so chats and home tabs are skipped
 * even if the service is handed their text.
 */
object PaymentPageRules {
    val successMarkers = listOf(
        "支付成功",
        "付款成功",
        "交易成功",
        "收款成功",
        "转账成功",
        "退款成功",
        "充值成功",
        "提现成功",
        "支付完成",
    )

    val detailMarkers = listOf(
        "账单详情",
        "交易详情",
        "账单明细",
    )

    val detailOkMarkers = listOf(
        "已收款",
        "对方已收钱",
        "已存入零钱",
        "支付成功",
        "付款成功",
        "交易成功",
        "收款成功",
        "转账成功",
        "退款成功",
        "支付完成",
    )

    val pendingMarkers = listOf(
        "待朋友确认",
        "待对方确认",
        "待对方收款",
        "等待付款",
        "支付中",
        "处理中",
        "待支付",
    )

    val failedMarkers = listOf(
        "支付失败",
        "交易关闭",
        "已关闭",
        "已取消",
        "已全额退款",
    )

    val chatMarkers = listOf(
        "按住说话",
        "按住 说话",
    )

    val amountLabels = listOf(
        "订单金额",
        "交易金额",
        "付款金额",
        "收款金额",
        "实付金额",
        "实付",
        "金额",
    )

    val merchantLabels = listOf(
        "商户全称",
        "商户名称",
        "交易对方",
        "收款方全称",
        "收款方",
        "付款方",
        "对方账户",
        "商家",
    )

    val accountLabels = listOf(
        "支付方式",
        "付款方式",
        "付款账户",
        "扣款账户",
        "交易方式",
        "收款账户",
        "支付账户",
    )

    val timeLabels = listOf(
        "支付时间",
        "交易时间",
        "收款时间",
        "转账时间",
        "创建时间",
        "付款时间",
    )

    val statusWords = listOf(
        "当前状态",
        "交易状态",
        "支付状态",
        "转账说明",
    )

    val buttons = listOf(
        "完成并返回",
        "返回商家",
        "查看账单",
        "我知道了",
        "完成",
        "确定",
        "关闭",
        "返回",
        "账单详情",
    )

    val transferMarkers = listOf(
        "充值成功",
        "提现成功",
        "转入零钱通",
        "转出零钱通",
        "账户互转",
        "还款成功",
        "信用卡还款",
        "转账成功",
    )

    val incomeMarkers = listOf(
        "收款成功",
        "退款成功",
        "已收款",
        "收款到账",
        "你已收款",
    )
}
