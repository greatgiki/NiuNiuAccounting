package com.example.localledger.ui.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localledger.core.money.MoneyParser
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.voice.VoiceLedgerDraft
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.UiOutcome
import com.example.localledger.ui.component.LedgerCard
import com.example.localledger.ui.component.rememberLedgerHaptic
import com.example.localledger.ui.editor.TransactionEditorViewModel
import com.example.localledger.ui.theme.LocalLedgerDarkTheme
import com.example.localledger.ui.theme.amountColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val NO_RECOGNIZER =
    "这台手机没有可用的语音识别。请点输入框，使用输入法上的语音键，或直接输入。"

data class VoiceDraftSave(
    val type: TransactionType,
    val amountText: String,
    val categoryId: String,
    val accountId: String,
    val date: LocalDate,
    val time: LocalTime,
    val note: String,
)

@Immutable
internal data class EditableVoiceDraft(
    val key: String,
    val type: TransactionType,
    val amountText: String,
    val categoryId: String?,
    val accountId: String?,
    val date: LocalDate,
    val time: LocalTime,
    val note: String,
    val heardCategory: String?,
)

private sealed interface VoicePhase {
    data object Starting : VoicePhase
    data object Rationale : VoicePhase
    data class Listening(val partial: String, val hint: String?) : VoicePhase
    data class Typing(val message: String, val text: String) : VoicePhase
    data class Review(
        val heard: String,
        val drafts: List<EditableVoiceDraft>,
        val saving: Boolean,
        val error: String?,
    ) : VoicePhase
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBookkeeping(
    viewModel: TransactionEditorViewModel,
    defaultAccountId: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val capture = remember { DeviceSpeechCapture(context.applicationContext) }
    DisposableEffect(capture) {
        onDispose { capture.release() }
    }
    val expenseSnapshot by viewModel.voiceExpenseCategories.collectAsStateWithLifecycle()
    val incomeSnapshot by viewModel.voiceIncomeCategories.collectAsStateWithLifecycle()
    val accountSnapshot by viewModel.voiceAccounts.collectAsStateWithLifecycle()
    val expense = expenseSnapshot.active
    val income = incomeSnapshot.active
    val accounts = accountSnapshot.active
    val zoneId = viewModel.zoneId
    val phaseState = remember { mutableStateOf<VoicePhase>(VoicePhase.Starting) }
    var phase by phaseState
    var listenToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val finishJob = remember { mutableStateOf<Job?>(null) }
    val haptic = rememberLedgerHaptic()

    fun reviewOf(text: String): VoicePhase {
        val drafts = viewModel.parseVoice(text).map { draft ->
            draft.toEditable(defaultAccountId, accounts, zoneId)
        }
        return if (drafts.isEmpty()) {
            VoicePhase.Typing("没有听出可以记账的内容。可以改成「午饭35，打车20」，或直接输入。", text)
        } else {
            VoicePhase.Review(heard = text, drafts = drafts, saving = false, error = null)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listenToken += 1
        } else {
            phase = VoicePhase.Typing(
                "没有麦克风权限。请点输入框，使用输入法上的语音键，或直接输入。",
                "",
            )
        }
    }
    val intentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        phase = if (result.resultCode == Activity.RESULT_OK && text.isNotBlank()) {
            reviewOf(text)
        } else {
            VoicePhase.Typing("没有识别到内容。请使用输入法上的语音键，或直接输入。", text)
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) listenToken += 1 else phase = VoicePhase.Rationale
    }

    LaunchedEffect(listenToken) {
        if (listenToken == 0) return@LaunchedEffect
        finishJob.value?.cancel()
        when (capture.route()) {
            SpeechRoute.TEXT -> phase = VoicePhase.Typing(NO_RECOGNIZER, "")
            SpeechRoute.INTENT -> intentLauncher.launch(capture.recognizeIntent())
            SpeechRoute.RECOGNIZER -> {
                phase = VoicePhase.Listening(partial = "", hint = null)
                capture.start(object : SpeechCallbacks {
                    override fun onPartial(text: String) {
                        val current = phaseState.value as? VoicePhase.Listening ?: return
                        phaseState.value = current.copy(partial = text, hint = null)
                    }

                    override fun onFinal(text: String) {
                        if (phaseState.value !is VoicePhase.Listening) return
                        phaseState.value = if (text.isBlank()) {
                            VoicePhase.Listening("", "没听清。请再说一次，或直接输入。")
                        } else {
                            reviewOf(text)
                        }
                    }

                    override fun onError(code: Int) {
                        if (phaseState.value !is VoicePhase.Listening) return
                        val partial = (phaseState.value as? VoicePhase.Listening)?.partial.orEmpty()
                        phaseState.value = if (speechErrorGivesUp(code)) {
                            VoicePhase.Typing(speechErrorMessage(code), partial)
                        } else {
                            VoicePhase.Listening(partial, speechErrorMessage(code))
                        }
                    }
                })
            }
        }
    }

    if (phase is VoicePhase.Rationale) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("语音记账") },
            text = {
                Text("需要麦克风权限，才能把你说的话在本机转成金额、分类和备注。识别使用手机自带的语音服务，账本不会上传。")
            },
            confirmButton = {
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
        )
    }

    val sheetPhase = phase
    if (sheetPhase is VoicePhase.Listening || sheetPhase is VoicePhase.Typing || sheetPhase is VoicePhase.Review) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                if (sheetPhase !is VoicePhase.Review || !sheetPhase.saving) onDismiss()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            when (sheetPhase) {
                is VoicePhase.Listening -> ListeningBody(
                    partial = sheetPhase.partial,
                    hint = sheetPhase.hint,
                    onDone = {
                        haptic()
                        capture.stop()
                        finishJob.value?.cancel()
                        finishJob.value = scope.launch {
                            delay(800)
                            val current = phaseState.value as? VoicePhase.Listening ?: return@launch
                            phaseState.value = if (current.partial.isNotBlank()) {
                                reviewOf(current.partial)
                            } else {
                                current.copy(hint = "没听清。请再说一次，或直接输入。")
                            }
                        }
                    },
                    onRetry = {
                        listenToken += 1
                    },
                    onType = {
                        phase = VoicePhase.Typing(
                            "请点输入框，使用输入法上的语音键，或直接输入。",
                            sheetPhase.partial,
                        )
                    },
                    onCancel = onDismiss,
                )
                is VoicePhase.Typing -> TypingBody(
                    message = sheetPhase.message,
                    text = sheetPhase.text,
                    onText = { phase = sheetPhase.copy(text = it.take(200)) },
                    onSubmit = {
                        val trimmed = sheetPhase.text.trim()
                        if (trimmed.isEmpty()) return@TypingBody
                        phase = reviewOf(trimmed)
                    },
                    onCancel = onDismiss,
                )
                is VoicePhase.Review -> ReviewBody(
                    phase = sheetPhase,
                    expense = expense,
                    income = income,
                    accounts = accounts,
                    onChange = { next -> phase = next },
                    onCancel = onDismiss,
                    onConfirm = {
                        val problem = sheetPhase.drafts.firstNotNullOfOrNull { it.problem(expense, income, accounts) }
                        if (problem != null) {
                            phase = sheetPhase.copy(error = problem)
                            return@ReviewBody
                        }
                        haptic()
                        phase = sheetPhase.copy(saving = true, error = null)
                        scope.launch {
                            val outcome = viewModel.saveVoiceDrafts(sheetPhase.drafts.map { it.toSave() })
                            if (outcome is UiOutcome.Failure) {
                                phase = sheetPhase.copy(saving = false, error = outcome.message)
                            }
                        }
                    },
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun ListeningBody(
    partial: String,
    hint: String?,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    onType: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VoiceListeningMark()
        Spacer(Modifier.height(12.dp))
        Text(
            text = partial.ifBlank { "正在听…" },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "例如：午饭三十五，打车二十",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Text(
            "使用手机自带语音，账本不会上传",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("说完了")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onRetry) { Text("再说一次") }
            TextButton(onClick = onType) { Text("改为输入") }
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

/**
 * Mic pulse and bars are drawn from an [Animatable] read in the draw phase,
 * so the listening sheet does not recompose on every frame.
 */
@Composable
private fun VoiceListeningMark() {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        phase.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1400, easing = LinearEasing)),
        )
    }
    val color = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Outlined.Mic,
            contentDescription = "正在聆听",
            tint = color,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    alpha = 0.55f + 0.45f * abs(sin(phase.value * 2.0 * PI)).toFloat()
                },
        )
        Canvas(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 96.dp, height = 28.dp)
                .semantics { contentDescription = "语音波形" },
        ) {
            val travel = phase.value
            val angle = (travel * 2.0 * PI).toFloat()
            val count = 5
            val slot = size.width / (count + 1)
            val barWidth = slot * 0.42f
            for (index in 0 until count) {
                val wave = abs(sin(angle + index * 0.85f))
                val barHeight = size.height * (0.28f + 0.72f * wave)
                val left = slot * (index + 1) - barWidth / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, (size.height - barHeight) / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
    }
}

@Composable
private fun TypingBody(
    message: String,
    text: String,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Text("语音记账", style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("口述内容") },
            placeholder = { Text("午饭35，打车20") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("解析")
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("取消") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewBody(
    phase: VoicePhase.Review,
    expense: List<Category>,
    income: List<Category>,
    accounts: List<Account>,
    onChange: (VoicePhase.Review) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var editingDate by remember { mutableStateOf<String?>(null) }
    var editingTime by remember { mutableStateOf<String?>(null) }
    val problem = phase.drafts.firstNotNullOfOrNull { it.problem(expense, income, accounts) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("确认后再记入", style = MaterialTheme.typography.titleMedium)
        Text(
            "还没入账，每一项都可以改",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        if (phase.heard.isNotBlank()) {
            Text(
                "听到：${phase.heard}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        phase.drafts.forEachIndexed { index, draft ->
            DraftCard(
                index = index,
                count = phase.drafts.size,
                draft = draft,
                expense = expense,
                income = income,
                accounts = accounts,
                onDraft = { updated ->
                    onChange(phase.copy(drafts = phase.drafts.map { if (it.key == draft.key) updated else it }, error = null))
                },
                onRemove = {
                    onChange(phase.copy(drafts = phase.drafts.filter { it.key != draft.key }, error = null))
                },
                onPickDate = { editingDate = draft.key },
                onPickTime = { editingTime = draft.key },
            )
            Spacer(Modifier.height(12.dp))
        }
        val message = phase.error ?: problem
        if (message != null) {
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onConfirm,
            enabled = !phase.saving && problem == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (phase.saving) "保存中…" else "确认记入")
        }
        TextButton(
            onClick = onCancel,
            enabled = !phase.saving,
            modifier = Modifier.align(Alignment.End),
        ) { Text("取消") }
    }

    val dateDraft = phase.drafts.firstOrNull { it.key == editingDate }
    if (dateDraft != null) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateDraft.date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { editingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        onChange(
                            phase.copy(
                                drafts = phase.drafts.map { if (it.key == dateDraft.key) it.copy(date = date) else it },
                            ),
                        )
                    }
                    editingDate = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editingDate = null }) { Text("取消") } },
        ) { DatePicker(state = pickerState) }
    }

    val timeDraft = phase.drafts.firstOrNull { it.key == editingTime }
    if (timeDraft != null) {
        val timeState = rememberTimePickerState(
            initialHour = timeDraft.time.hour,
            initialMinute = timeDraft.time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingTime = null },
            confirmButton = {
                TextButton(onClick = {
                    val time = LocalTime.of(timeState.hour, timeState.minute)
                    onChange(
                        phase.copy(
                            drafts = phase.drafts.map { if (it.key == timeDraft.key) it.copy(time = time) else it },
                        ),
                    )
                    editingTime = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editingTime = null }) { Text("取消") } },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun DraftCard(
    index: Int,
    count: Int,
    draft: EditableVoiceDraft,
    expense: List<Category>,
    income: List<Category>,
    accounts: List<Account>,
    onDraft: (EditableVoiceDraft) -> Unit,
    onRemove: () -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val categories = if (draft.type == TransactionType.INCOME) income else expense
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (count > 1) "第 ${index + 1} 笔" else "这一笔",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (count > 1) {
                    TextButton(onClick = onRemove) { Text("删除") }
                }
            }
            TypeSwitch(type = draft.type, onType = { type ->
                val stillValid = categoriesFor(type, expense, income).any { it.id == draft.categoryId }
                onDraft(draft.copy(type = type, categoryId = draft.categoryId.takeIf { stillValid }, heardCategory = null))
            })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = { onDraft(draft.copy(amountText = it.take(16))) },
                label = { Text("金额") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OptionMenu(
                label = "分类",
                value = categories.firstOrNull { it.id == draft.categoryId }?.name ?: "请选择",
                options = categories.map { it.id to it.name },
                onSelect = { onDraft(draft.copy(categoryId = it, heardCategory = null)) },
            )
            draft.heardCategory?.let { guessed ->
                Text(
                    "接近「$guessed」，当前账本里没有同名分类",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            OptionMenu(
                label = "账户",
                value = accounts.firstOrNull { it.id == draft.accountId }?.name ?: "请选择",
                options = accounts.map { it.id to it.name },
                onSelect = { onDraft(draft.copy(accountId = it)) },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickDate, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.DateRange, contentDescription = "选择日期", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(DateTimeFormatters.date(draft.date), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = onPickTime, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = "选择时间", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(draft.time.toString().substring(0, 5))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { onDraft(draft.copy(note = it.take(200))) },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun TypeSwitch(type: TransactionType, onType: (TransactionType) -> Unit) {
    val options = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val active = amountColor(option)
            val onActive = if (LocalLedgerDarkTheme.current) Color(0xFF1C1915) else Color(0xFFFFF9F2)
            SegmentedButton(
                selected = type == option,
                onClick = { onType(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = active,
                    activeContentColor = onActive,
                    activeBorderColor = active,
                ),
            ) {
                Text(if (option == TransactionType.INCOME) "收入" else "支出")
            }
        }
    }
}

@Composable
private fun OptionMenu(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("$label：$value", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun categoriesFor(type: TransactionType, expense: List<Category>, income: List<Category>): List<Category> =
    if (type == TransactionType.INCOME) income else expense

private fun EditableVoiceDraft.problem(
    expense: List<Category>,
    income: List<Category>,
    accounts: List<Account>,
): String? {
    if (amountText.isBlank()) return "请填写金额"
    val account = accounts.firstOrNull { it.id == accountId } ?: return "请选择账户"
    val scale = runCatching { CurrencyCatalog.of(account.currencyCode).minorDigits }.getOrDefault(2)
    try {
        MoneyParser.toMinor(amountText, scale)
    } catch (error: LedgerException) {
        return error.message ?: "金额不正确"
    }
    val pool = categoriesFor(type, expense, income)
    if (categoryId == null || pool.none { it.id == categoryId }) return "请选择分类"
    return null
}

private fun EditableVoiceDraft.toSave() = VoiceDraftSave(
    type = type,
    amountText = amountText.trim(),
    categoryId = categoryId.orEmpty(),
    accountId = accountId.orEmpty(),
    date = date,
    time = time,
    note = note.trim(),
)

internal fun VoiceLedgerDraft.toEditable(
    defaultAccountId: String?,
    accounts: List<Account>,
    zoneId: ZoneId,
    now: LocalTime = LocalTime.now(zoneId),
): EditableVoiceDraft {
    val resolvedAccount = accountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: defaultAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: accounts.firstOrNull()?.id
    val amountText = amount?.stripTrailingZeros()?.toPlainString()?.takeUnless { it == "0" }.orEmpty()
    return EditableVoiceDraft(
        key = UUID.randomUUID().toString(),
        type = if (type == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
        amountText = amountText,
        categoryId = categoryId,
        accountId = resolvedAccount,
        date = date,
        time = now.withSecond(0).withNano(0),
        note = note.take(200),
        heardCategory = categoryName.takeIf { categoryId == null && !it.isNullOrBlank() },
    )
}
