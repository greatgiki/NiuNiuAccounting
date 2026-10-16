package com.example.localledger.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIcon(val key: String, val label: String, val image: ImageVector)

object CategoryIcons {
    private class IconSpec(val key: String, val label: String, val image: () -> ImageVector)

    private val specs = listOf(
        IconSpec("restaurant", "餐饮") { Icons.Default.Restaurant },
        IconSpec("directions_car", "交通") { Icons.Default.DirectionsCar },
        IconSpec("shopping_bag", "购物") { Icons.Default.ShoppingBag },
        IconSpec("home", "居住") { Icons.Default.Home },
        IconSpec("sports_esports", "娱乐") { Icons.Default.SportsEsports },
        IconSpec("medical_services", "医疗") { Icons.Default.MedicalServices },
        IconSpec("school", "教育") { Icons.Default.School },
        IconSpec("phone_android", "通讯") { Icons.Default.PhoneAndroid },
        IconSpec("payments", "工资") { Icons.Default.Payments },
        IconSpec("redeem", "奖金") { Icons.Default.CardGiftcard },
        IconSpec("work", "工作") { Icons.Default.Work },
        IconSpec("undo", "退款") { Icons.AutoMirrored.Filled.Undo },
        IconSpec("local_cafe", "饮品") { Icons.Default.LocalCafe },
        IconSpec("movie", "影视") { Icons.Default.Movie },
        IconSpec("flight", "旅行") { Icons.Default.Flight },
        IconSpec("pets", "宠物") { Icons.Default.Pets },
        IconSpec("directions_run", "运动") { Icons.AutoMirrored.Filled.DirectionsRun },
        IconSpec("savings", "储蓄") { Icons.Default.Savings },
        IconSpec("account_balance_wallet", "钱包") { Icons.Default.AccountBalanceWallet },
        IconSpec("credit_card", "信用卡") { Icons.Default.CreditCard },
        IconSpec("attach_money", "理财") { Icons.Default.AttachMoney },
        IconSpec("more_horiz", "其他") { Icons.Default.MoreHoriz },
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
                Icons.AutoMirrored.Filled.ArrowBack
                Icons.AutoMirrored.Filled.Backspace
                Icons.Default.AccessTime
                Icons.Default.Add
                Icons.Default.DateRange
                Icons.Default.Delete
                Icons.Default.SwapHoriz
            }
        }
        worker.name = "icon-prewarm"
        worker.isDaemon = true
        worker.start()
    }
}
