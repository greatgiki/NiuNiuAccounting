package com.example.localledger.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.UiOutcome
import com.example.localledger.ui.common.runOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CategoryManageUiState(
    val isLoading: Boolean = true,
    val type: CategoryType,
    val categories: List<Category> = emptyList(),
    val error: String? = null,
)

sealed interface CategoryEffect {
    data class ShowMessage(val message: String) : CategoryEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    appSession: AppSession,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    val categoryType: CategoryType =
        savedStateHandle.get<String>("categoryType")
            ?.let { runCatching { CategoryType.valueOf(it) }.getOrNull() }
            ?: CategoryType.EXPENSE

    private val effectChannel = Channel<CategoryEffect>(Channel.BUFFERED)
    private val refreshFlow = MutableStateFlow(0)
    val effects = effectChannel.receiveAsFlow()

    val uiState: StateFlow<CategoryManageUiState> = combine(
        appSession.state.map { it.currentLedgerId }, refreshFlow,
    ) { ledgerId, _ -> ledgerId }
        .flatMapLatest { ledgerId ->
            if (ledgerId == null) {
                flowOf(CategoryManageUiState(isLoading = false, type = categoryType))
            } else {
                categoryRepository.observeCategories(ledgerId, categoryType)
                    .map { CategoryManageUiState(isLoading = false, type = categoryType, categories = it) }
            }
        }
        .catch { error ->
            emit(
                CategoryManageUiState(
                    isLoading = false,
                    type = categoryType,
                    error = error.message ?: "读取分类失败",
                ),
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CategoryManageUiState(type = categoryType),
        )

    suspend fun save(
        existing: Category?,
        ledgerId: String,
        name: String,
        iconKey: String,
        sortOrderText: String,
    ): UiOutcome {
        val now = System.currentTimeMillis()
        val category = Category(
            id = existing?.id ?: UUID.randomUUID().toString(),
            ledgerId = ledgerId,
            name = name.trim(),
            type = categoryType,
            iconKey = iconKey,
            sortOrder = sortOrderText.toIntOrNull() ?: 0,
            isArchived = existing?.isArchived ?: false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return runOutcome("保存分类失败") { categoryRepository.saveCategory(category) }
    }

    suspend fun archive(categoryId: String): UiOutcome =
        runOutcome("停用分类失败") { categoryRepository.archiveCategory(categoryId) }

    fun retry() { refreshFlow.value += 1 }
}
