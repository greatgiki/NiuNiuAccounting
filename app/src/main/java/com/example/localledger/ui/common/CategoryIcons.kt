package com.example.localledger.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIcon(val key: String, val label: String, val image: ImageVector)

object CategoryIcons {
    private class IconSpec(val key: String, val label: String, val image: () -> ImageVector)

    private val specs = listOf(
        IconSpec("restaurant", "餐饮") { Icons.Outlined.Restaurant },
        IconSpec("directions_car", "交通") { Icons.Outlined.DirectionsCar },
        IconSpec("shopping_bag", "购物") { Icons.Outlined.ShoppingBag },
        IconSpec("home", "居住") { Icons.Outlined.Home },
        IconSpec("sports_esports", "娱乐") { Icons.Outlined.SportsEsports },
        IconSpec("medical_services", "医疗") { Icons.Outlined.MedicalServices },
        IconSpec("school", "教育") { Icons.Outlined.School },
        IconSpec("phone_android", "通讯") { Icons.Outlined.PhoneAndroid },
        IconSpec("payments", "工资") { Icons.Outlined.Payments },
        IconSpec("redeem", "奖金") { Icons.Outlined.CardGiftcard },
        IconSpec("work", "工作") { Icons.Outlined.Work },
        IconSpec("undo", "退款") { Icons.AutoMirrored.Outlined.Undo },
        IconSpec("local_cafe", "饮品") { Icons.Outlined.LocalCafe },
        IconSpec("movie", "影视") { Icons.Outlined.Movie },
        IconSpec("flight", "旅行") { Icons.Outlined.Flight },
        IconSpec("pets", "宠物") { Icons.Outlined.Pets },
        IconSpec("directions_run", "运动") { Icons.AutoMirrored.Outlined.DirectionsRun },
        IconSpec("savings", "储蓄") { Icons.Outlined.Savings },
        IconSpec("account_balance_wallet", "钱包") { Icons.Outlined.AccountBalanceWallet },
        IconSpec("credit_card", "信用卡") { Icons.Outlined.CreditCard },
        IconSpec("attach_money", "理财") { Icons.Outlined.AttachMoney },
        IconSpec("more_horiz", "其他") { Icons.Outlined.MoreHoriz },
    )

    private val specByKey = specs.associateBy { it.key }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CategoryIcon>()

    val fallback: CategoryIcon
        get() = resolve("more_horiz")

    val all: List<CategoryIcon>
        get() = specs.map { resolve(it.key) }

    fun resolve(key: String?): CategoryIcon {
        val spec = specByKey[key] ?: specByKey.getValue("more_horiz")
        return cache.computeIfAbsent(spec.key) {
            CategoryIcon(spec.key, spec.label, spec.image())
        }
    }

    /** Build vectors off the main thread so the first 记一笔 frame does not inflate them. */
    fun prewarm() {
        val worker = Thread {
            runCatching {
                specs.forEach { resolve(it.key) }
                Icons.AutoMirrored.Outlined.ArrowBack
                Icons.AutoMirrored.Outlined.Backspace
                Icons.Outlined.AccessTime
                Icons.Outlined.Add
                Icons.Outlined.DateRange
                Icons.Outlined.Delete
                Icons.Outlined.SwapHoriz
            }
        }
        worker.name = "icon-prewarm"
        worker.isDaemon = true
        worker.start()
    }
}
