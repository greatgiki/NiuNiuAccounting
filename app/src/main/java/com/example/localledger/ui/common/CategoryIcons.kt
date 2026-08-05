package com.example.localledger.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
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
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIcon(val key: String, val label: String, val image: ImageVector)

object CategoryIcons {
    val fallback = CategoryIcon("more_horiz", "其他", Icons.Default.MoreHoriz)

    val all: List<CategoryIcon> = listOf(
        CategoryIcon("restaurant", "餐饮", Icons.Default.Restaurant),
        CategoryIcon("directions_car", "交通", Icons.Default.DirectionsCar),
        CategoryIcon("shopping_bag", "购物", Icons.Default.ShoppingBag),
        CategoryIcon("home", "居住", Icons.Default.Home),
        CategoryIcon("sports_esports", "娱乐", Icons.Default.SportsEsports),
        CategoryIcon("medical_services", "医疗", Icons.Default.MedicalServices),
        CategoryIcon("school", "教育", Icons.Default.School),
        CategoryIcon("phone_android", "通讯", Icons.Default.PhoneAndroid),
        CategoryIcon("payments", "工资", Icons.Default.Payments),
        CategoryIcon("redeem", "奖金", Icons.Default.CardGiftcard),
        CategoryIcon("work", "工作", Icons.Default.Work),
        CategoryIcon("undo", "退款", Icons.AutoMirrored.Filled.Undo),
        CategoryIcon("local_cafe", "饮品", Icons.Default.LocalCafe),
        CategoryIcon("movie", "影视", Icons.Default.Movie),
        CategoryIcon("flight", "旅行", Icons.Default.Flight),
        CategoryIcon("pets", "宠物", Icons.Default.Pets),
        CategoryIcon("directions_run", "运动", Icons.AutoMirrored.Filled.DirectionsRun),
        CategoryIcon("savings", "储蓄", Icons.Default.Savings),
        CategoryIcon("account_balance_wallet", "钱包", Icons.Default.AccountBalanceWallet),
        CategoryIcon("credit_card", "信用卡", Icons.Default.CreditCard),
        CategoryIcon("attach_money", "理财", Icons.Default.AttachMoney),
        fallback,
    )

    private val byKey = all.associateBy { it.key }

    fun resolve(key: String?): CategoryIcon = byKey[key] ?: fallback
}
