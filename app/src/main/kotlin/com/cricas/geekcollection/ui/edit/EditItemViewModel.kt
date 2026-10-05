package com.cricas.geekcollection.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cricas.geekcollection.core.lookup.LookupOutcome
import com.cricas.geekcollection.core.lookup.LookupResult
import com.cricas.geekcollection.core.lookup.LookupService
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.model.Platforms
import com.cricas.geekcollection.core.model.ProgressStatus
import com.cricas.geekcollection.core.model.newSyncId
import com.cricas.geekcollection.data.ImageStore
import com.cricas.geekcollection.data.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditFormState(
    val id: Long = 0L,
    val title: String = "",
    val category: ItemCategory = ItemCategory.VIDEO_GAME,
    val platform: String = "",
    /** True when the platform is typed manually instead of picked from the list. */
    val customPlatform: Boolean = false,
    val completionPercent: Int = 0,
    val progressStatus: ProgressStatus = ProgressStatus.IN_PROGRESS,
    val platinum: Boolean = false,
    val backlog: Boolean = false,
    val description: String = "",
    val creator: String = "",
    val publisher: String = "",
    val releaseYear: String = "",
    val barcode: String = "",
    val notes: String = "",
    val coverUrl: String? = null,
    val localImagePath: String? = null,
    val favorite: Boolean = false,
    val externalSource: String? = null,
    val externalId: String? = null,
    val createdAt: Long = 0L,
    val syncId: String? = null,
    val titleError: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
) {
    fun toItem(): CollectionItem = CollectionItem(
        id = id,
        title = title,
        category = category,
        platform = platform.takeIf { it.isNotBlank() },
        completionPercent = completionPercent,
        progressStatus = progressStatus,
        platinum = platinum,
        backlog = backlog,
        description = description,
        creator = creator,
        publisher = publisher,
        releaseYear = releaseYear.trim().toIntOrNull(),
        barcode = barcode,
        coverUrl = coverUrl,
        localImagePath = localImagePath,
        notes = notes,
        favorite = favorite,
        externalSource = externalSource,
        externalId = externalId,
        createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
        syncId = syncId ?: newSyncId(),
    ).normalized()

    companion object {
        fun from(item: CollectionItem): EditFormState = EditFormState(
            id = item.id,
            title = item.title,
            category = item.category,
            platform = item.platform.orEmpty(),
            customPlatform = item.platform?.let { Platforms.byName(it) == null } ?: false,
            completionPercent = item.completionPercent ?: 0,
            progressStatus = item.progressStatus,
            platinum = item.platinum,
            backlog = item.backlog,
            description = item.description.orEmpty(),
            creator = item.creator.orEmpty(),
            publisher = item.publisher.orEmpty(),
            releaseYear = item.releaseYear?.toString().orEmpty(),
            barcode = item.barcode.orEmpty(),
            notes = item.notes.orEmpty(),
            coverUrl = item.coverUrl,
            localImagePath = item.localImagePath,
            favorite = item.favorite,
            externalSource = item.externalSource,
            externalId = item.externalId,
            createdAt = item.createdAt,
            syncId = item.syncId,
            loading = false,
        )
    }
}

sealed interface OnlineSearchState {
    data object Hidden : OnlineSearchState
    data object Loading : OnlineSearchState
    data class Results(val outcome: LookupOutcome) : OnlineSearchState
    data class Error(val message: String) : OnlineSearchState
}

class EditItemViewModel(
    private val repository: ItemRepository,
    private val imageStore: ImageStore,
    private val lookupService: LookupService,
    itemId: Long?,
    draft: CollectionItem?,
) : ViewModel() {

    private val _form = MutableStateFlow(EditFormState())
    val form: StateFlow<EditFormState> = _form.asStateFlow()

    private val _search = MutableStateFlow<OnlineSearchState>(OnlineSearchState.Hidden)
    val search: StateFlow<OnlineSearchState> = _search.asStateFlow()

    /** URI handed to the camera app; kept here so it survives configuration changes. */
    var pendingCaptureUri: Uri? = null

    init {
        when {
            itemId != null -> viewModelScope.launch {
                val item = repository.getItem(itemId)
                _form.value = if (item != null) EditFormState.from(item) else EditFormState(loading = false)
            }
            draft != null -> _form.value = EditFormState.from(draft)
            else -> _form.value = EditFormState(loading = false)
        }
    }

    fun update(transform: EditFormState.() -> EditFormState) {
        _form.update { it.transform().copy(titleError = false) }
    }

    fun setCategory(category: ItemCategory) = update {
        copy(
            category = category,
            platform = if (category.supportsPlatform) platform else "",
            completionPercent = if (category.supportsCompletion) completionPercent else 0,
            progressStatus = if (category.supportsCompletion) progressStatus else ProgressStatus.IN_PROGRESS,
            platinum = category.supportsPlatinum && platinum,
            backlog = category.supportsBacklog && backlog,
        )
    }

    fun onPhotoCaptured(success: Boolean) {
        val uri = pendingCaptureUri ?: return
        pendingCaptureUri = null
        if (success) onPhotoPicked(uri)
    }

    fun onPhotoPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            runCatching { imageStore.persist(uri) }.onSuccess { path ->
                val previous = _form.value.localImagePath
                _form.update { it.copy(localImagePath = path) }
                previous?.let { imageStore.delete(it) }
            }
        }
    }

    fun removePhoto() {
        val previous = _form.value.localImagePath ?: return
        _form.update { it.copy(localImagePath = null) }
        viewModelScope.launch { imageStore.delete(previous) }
    }

    fun searchOnline() {
        val current = _form.value
        if (current.title.isBlank()) {
            _form.update { it.copy(titleError = true) }
            return
        }
        _search.value = OnlineSearchState.Loading
        viewModelScope.launch {
            runCatching {
                lookupService.searchByTitle(current.title, current.category, current.platform.takeIf { it.isNotBlank() })
            }.onSuccess { _search.value = OnlineSearchState.Results(it) }
                .onFailure { _search.value = OnlineSearchState.Error(it.message ?: it::class.java.simpleName) }
        }
    }

    fun applyResult(result: LookupResult) {
        _form.update { f ->
            f.copy(
                title = result.title,
                category = result.category ?: f.category,
                platform = result.platform ?: f.platform,
                customPlatform = result.platform?.let { Platforms.byName(it) == null } ?: f.customPlatform,
                description = result.description ?: f.description,
                creator = result.creator ?: f.creator,
                publisher = result.publisher ?: f.publisher,
                releaseYear = result.releaseYear?.toString() ?: f.releaseYear,
                barcode = result.barcode ?: f.barcode,
                coverUrl = result.coverUrl ?: f.coverUrl,
                externalSource = result.source,
                externalId = result.externalId,
            )
        }
        _search.value = OnlineSearchState.Hidden
    }

    fun dismissSearch() {
        _search.value = OnlineSearchState.Hidden
    }

    fun save(onSaved: (Long) -> Unit) {
        val current = _form.value
        if (current.title.isBlank()) {
            _form.update { it.copy(titleError = true) }
            return
        }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = repository.save(current.toItem())
            _form.update { it.copy(saving = false, id = id) }
            onSaved(id)
        }
    }
}
