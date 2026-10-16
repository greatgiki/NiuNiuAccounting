package com.example.localledger.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

object RatioMath {
    fun ratio(part: Long, total: Long, scale: Int = 8): BigDecimal {
        if (part <= 0L || total <= 0L) return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), scale, RoundingMode.HALF_UP)
    }

    /** 图表用比例。先做精确除法，再转成绘制所需的小数，避免大额 Long 直接 toFloat。 */
    fun fraction(part: Long, total: Long): Float = ratio(part, total).toFloat()

    fun percentLabel(part: Long, total: Long): String {
        val percent = ratio(part, total, scale = 4).movePointRight(2).setScale(1, RoundingMode.HALF_UP)
        return "$percent%"
    }
}
