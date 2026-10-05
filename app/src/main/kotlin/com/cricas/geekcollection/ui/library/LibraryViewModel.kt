package com.cricas.geekcollection.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.data.ItemRepository
import com.cricas.geekcollection.data.ProgressFilter
import com.cricas.geekcollection.data.SortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class LibraryFilter(
    val query: String = "",
    val category: ItemCategory? = null,
    val favoritesOnly: Boolean = false,
    val progress: ProgressFilter? = null,
    val sort: SortOrder = SortOrder.RECENT,
)

data class LibraryUiState(
    val items: List<CollectionItem> = emptyList(),
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val repository: ItemRepository) : ViewModel() {

    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter

    val uiState: StateFlow<LibraryUiState> = combine(
        _filter.flatMapLatest { f -> repository.observeItems(f.query, f.category, f.favoritesOnly, f.progress, f.sort) },
        repository.observeCount(),
        repository.observeCompletedCount(),
    ) { items, total, completed ->
        LibraryUiState(items = items, totalCount = total, completedCount = completed, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(query: String) = _filter.value.let { _filter.value = it.copy(query = query) }

    fun setCategory(category: ItemCategory?) = _filter.value.let { _filter.value = it.copy(category = category) }

    fun toggleFavorites() = _filter.value.let { _filter.value = it.copy(favoritesOnly = !it.favoritesOnly) }

    fun setSort(sort: SortOrder) = _filter.value.let { _filter.value = it.copy(sort = sort) }

    fun setProgress(progress: ProgressFilter?) = _filter.value.let { _filter.value = it.copy(progress = progress) }
}
