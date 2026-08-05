package com.example.localledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.home.SectionTitle
import com.example.localledger.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    appSession: AppSession,
    navController: NavHostController,
) {
    val session by appSession.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SectionTitle("账本与分类")
        Card(modifier = Modifier.fillMaxWidth()) {
            SettingsEntry("账本管理") { navController.navigate(Routes.LEDGERS) }
            HorizontalDivider()
            SettingsEntry("账户管理") { navController.navigate(Routes.ACCOUNTS) }
            HorizontalDivider()
            SettingsEntry("支出分类") {
                navController.navigate(Routes.categories(CategoryType.EXPENSE))
            }
            HorizontalDivider()
            SettingsEntry("收入分类") {
                navController.navigate(Routes.categories(CategoryType.INCOME))
            }
        }

        SectionTitle("外观")
        Card(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { appSession.setTheme(mode) } }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = session.themeMode == mode,
                        onClick = { scope.launch { appSession.setTheme(mode) } },
                    )
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (index < ThemeMode.entries.size - 1) HorizontalDivider()
            }
        }

        SectionTitle("数据与隐私")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "数据仅保存在本机",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "本应用完全离线，不会上传任何数据。\n" +
                        "卸载应用、清除应用数据或恢复出厂设置后，账本数据将被删除且无法恢复。\n" +
                        "当前版本不支持备份与恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SettingsEntry(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
