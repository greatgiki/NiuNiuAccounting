package com.example.localledger.ui.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.CategoryIcons
import com.example.localledger.ui.component.CollapsibleSectionHeader
import com.example.localledger.ui.component.ConfirmDeleteDialog
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.component.SubmitGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    appSession: AppSession,
    navController: NavHostController,
    viewModel: CategoryManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by appSession.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editTarget by remember { mutableStateOf<Category?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<Category?>(null) }
    var archivedExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveGate = remember { SubmitGate() }
    val archiveGate = remember { SubmitGate() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CategoryEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val active = state.categories.filter { !it.isArchived }
    val archived = state.categories.filter { it.isArchived }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (viewModel.categoryType == CategoryType.EXPENSE) "支出分类" else "收入分类")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { com.example.localledger.ui.component.LedgerSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                saveGate.reset()
                showCreate = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "新建分类")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingState() }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding)) {
                ErrorState(state.error ?: "读取分类失败", onRetry = viewModel::retry)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                if (active.isEmpty()) {
                    item(key = "empty") { EmptyState("暂无分类，点击右下角新建") }
                }
                items(active, key = { it.id }, contentType = { "category" }) { category ->
                    CategoryRow(
                        modifier = Modifier.animateItem(),
                        category = category,
                        onEdit = {
                            saveGate.reset()
                            editTarget = category
                        },
                        onArchive = { archiveTarget = category },
                    )
                    HorizontalDivider()
                }
                if (archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        CollapsibleSectionHeader(
                            title = "已停用（${archived.size}）",
                            expanded = archivedExpanded,
                            onToggle = { archivedExpanded = !archivedExpanded },
                        )
                    }
                    if (archivedExpanded) {
                    items(archived, key = { "archived-${it.id}" }, contentType = { "archived-category" }) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                CategoryIcons.resolve(category.iconKey).image,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.padding(4.dp))
                            Text(
                                category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CategoryEditDialog(
            category = null,
            submitting = saveGate.submitting,
            errorMessage = saveGate.error,
            onConfirm = { name, iconKey, sortText ->
                val ledgerId = session.currentLedgerId ?: return@CategoryEditDialog
                saveGate.launch(scope, {
                    viewModel.save(null, ledgerId, name, iconKey, sortText)
                }) {
                    showCreate = false
                }
            },
            onDismiss = {
                if (!saveGate.submitting) {
                    saveGate.reset()
                    showCreate = false
                }
            },
        )
    }

    editTarget?.let { category ->
        CategoryEditDialog(
            category = category,
            submitting = saveGate.submitting,
            errorMessage = saveGate.error,
            onConfirm = { name, iconKey, sortText ->
                val ledgerId = session.currentLedgerId ?: return@CategoryEditDialog
                saveGate.launch(scope, {
                    viewModel.save(category, ledgerId, name, iconKey, sortText)
                }) {
                    editTarget = null
                }
            },
            onDismiss = {
                if (!saveGate.submitting) {
                    saveGate.reset()
                    editTarget = null
                }
            },
        )
    }

    archiveTarget?.let { category ->
        ConfirmDeleteDialog(
            title = "停用分类“${category.name}”？",
            message = "停用后不能再用于新流水，历史流水和统计仍会保留。",
            confirmLabel = "停用",
            submitting = archiveGate.submitting,
            errorMessage = archiveGate.error,
            onConfirm = {
                archiveGate.launch(scope, { viewModel.archive(category.id) }) {
                    archiveTarget = null
                }
            },
            onDismiss = {
                archiveGate.reset()
                archiveTarget = null
            },
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            CategoryIcons.resolve(category.iconKey).image,
            contentDescription = category.name,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(6.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("编辑") },
                onClick = {
                    menuExpanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("停用") },
                onClick = {
                    menuExpanded = false
                    onArchive()
                },
            )
        }
    }
}

@Composable
private fun CategoryEditDialog(
    category: Category?,
    onConfirm: (name: String, iconKey: String, sortText: String) -> Unit,
    onDismiss: () -> Unit,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var iconKey by remember { mutableStateOf(category?.iconKey ?: CategoryIcons.fallback.key) }
    var sortText by remember { mutableStateOf(category?.sortOrder?.toString() ?: "0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "新建分类" else "编辑分类") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text("图标", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    CategoryIcons.all.forEach { icon ->
                        FilterChip(
                            selected = iconKey == icon.key,
                            onClick = { iconKey = icon.key },
                            label = {
                                Icon(
                                    icon.image,
                                    contentDescription = icon.label,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        Spacer(Modifier.padding(2.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sortText,
                    onValueChange = { sortText = it.filter(Char::isDigit).take(5) },
                    label = { Text("排序（数字越小越靠前）") },
                    singleLine = true,
                    enabled = !submitting,
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (!submitting) onConfirm(name, iconKey, sortText) },
                enabled = name.isNotBlank() && !submitting,
            ) { Text(if (submitting) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
        },
    )
}
