package com.example.localledger.domain.importing

import com.example.localledger.domain.model.TransactionType

data class CategoryKeywordHit(val keyword: String, val categoryName: String)

object CategoryKeywordMapper {
    private data class Rule(val keywords: List<String>, val categoryName: String)

    private val expenseRules = listOf(
        Rule(listOf("美团", "饿了么", "肯德基", "麦当劳", "星巴克", "瑞幸", "餐饮", "外卖", "食堂"), "餐饮"),
        Rule(listOf("滴滴", "地铁", "公交", "高铁", "铁路", "12306", "加油", "停车", "曹操", "花小猪", "机票", "火车"), "交通"),
        Rule(listOf("淘宝", "天猫", "京东", "拼多多", "唯品会", "超市", "便利店"), "购物"),
        Rule(listOf("房租", "水电", "物业", "燃气", "电费", "水费"), "居住"),
        Rule(listOf("电影", "游戏", "腾讯视频", "爱奇艺", "优酷", "哔哩", "bilibili", "网易云"), "娱乐"),
        Rule(listOf("医院", "药店", "药房", "体检", "门诊"), "医疗"),
        Rule(listOf("学费", "培训", "课程", "书店"), "教育"),
        Rule(listOf("话费", "流量", "中国移动", "中国联通", "中国电信"), "通讯"),
    )

    private val incomeRules = listOf(
        Rule(listOf("退款"), "退款"),
        Rule(listOf("工资", "薪资", "代发"), "工资"),
        Rule(listOf("奖金", "年终奖"), "奖金"),
        Rule(listOf("兼职", "稿费"), "兼职"),
    )

    /** First matching merchant or bill keyword, or null when nothing in the rules hits. */
    fun match(type: TransactionType, text: String): CategoryKeywordHit? {
        val haystack = text.lowercase()
        if (type == TransactionType.INCOME && haystack.contains("退款")) {
            return CategoryKeywordHit("退款", "退款")
        }
        val rules = if (type == TransactionType.INCOME) incomeRules else expenseRules
        for (rule in rules) {
            val keyword = rule.keywords.firstOrNull { haystack.contains(it.lowercase()) } ?: continue
            return CategoryKeywordHit(keyword, rule.categoryName)
        }
        return null
    }

    fun suggestName(type: TransactionType, text: String): String = match(type, text)?.categoryName ?: "其他"
}
