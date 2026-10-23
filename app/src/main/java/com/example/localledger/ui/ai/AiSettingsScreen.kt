package com.example.localledger.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.ai.AiPreset
import com.example.localledger.domain.ai.AiWireFormat
import com.example.localledger.ui.component.LedgerCard

internal const val AI_PRIVACY_NOTE =
    "解析记账时，只发送你这句话，以及分类和账户的名称。问账本时，模型只收到它点名查询的汇总，金额来自本机账本。不会发送密钥、卡号或整本流水。密钥由系统密钥库加密，不进入备份和诊断记录。"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    navController: NavHostController,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val keyStatus by viewModel.keyStatus.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val testing by viewModel.testingConnection.collectAsStateWithLifecycle()
    var presetMenu by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf<String?>(null) }
    var model by remember { mutableStateOf<String?>(null) }
    var capText by remember { mutableStateOf<String?>(null) }
    var keyText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 助手") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("开启 AI 助手", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "默认关闭。关闭时，记账只使用本机规则。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = settings.enabled, onCheckedChange = viewModel::setEnabled)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("服务", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "预设可以改地址和模型。手机直接连接这个地址。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    OutlinedButton(
                        onClick = { presetMenu = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(settings.preset.label)
                    }
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        AiPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    presetMenu = false
                                    baseUrl = if (preset == AiPreset.CUSTOM) null else preset.baseUrl
                                    model = if (preset == AiPreset.CUSTOM) null else preset.model
                                    viewModel.applyPreset(preset)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("接口格式", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        val formats = AiWireFormat.entries
                        formats.forEachIndexed { index, format ->
                            SegmentedButton(
                                selected = settings.format == format,
                                onClick = { viewModel.setFormat(format) },
                                shape = SegmentedButtonDefaults.itemShape(index, formats.size),
                            ) {
                                Text(if (format == AiWireFormat.OPENAI_CHAT) "OpenAI" else "Anthropic")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = baseUrl ?: settings.baseUrl,
                        onValueChange = {
                            baseUrl = it.take(300)
                            viewModel.setBaseUrl(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("接口地址") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = model ?: settings.model,
                        onValueChange = {
                            model = it.take(120)
                            viewModel.setModel(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = capText ?: settings.dailyRequestCap.toString(),
                        onValueChange = { next ->
                            val digits = next.filter { it.isDigit() }.take(3)
                            capText = digits
                            digits.toIntOrNull()?.let(viewModel::setDailyCap)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("每日请求上限") },
                        supportingText = { Text("0 表示不限制。一次提问可能包含几次查询。") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("API 密钥", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (keyStatus) {
                            KeyStatus.Saved -> "已加密保存在本机"
                            KeyStatus.Unreadable -> "无法读取已保存的密钥，请重新填写"
                            else -> "还没有密钥"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it.take(400) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (keyStatus == KeyStatus.Saved) "更换密钥" else "API 密钥") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                        ),
                    )
                    Row {
                        TextButton(
                            onClick = {
                                viewModel.saveKey(keyText)
                                keyText = ""
                            },
                            enabled = keyText.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("保存密钥") }
                        if (keyStatus == KeyStatus.Saved) {
                            TextButton(
                                onClick = {
                                    keyText = ""
                                    viewModel.clearKey()
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("清除") }
                        }
                    }
                    Button(
                        onClick = { viewModel.testConnection(keyText) },
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(if (testing) "正在连接…" else "测试连接")
                    }
                    if (!message.isNullOrBlank()) {
                        Text(
                            message.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                AI_PRIVACY_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
