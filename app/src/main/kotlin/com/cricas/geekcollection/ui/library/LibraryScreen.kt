package com.cricas.geekcollection.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cricas.geekcollection.R
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.data.ProgressFilter
import com.cricas.geekcollection.data.SortOrder
import com.cricas.geekcollection.di.AppContainer
import com.cricas.geekcollection.ui.components.CompletionBar
import com.cricas.geekcollection.ui.components.ItemCover
import com.cricas.geekcollection.ui.components.icon
import com.cricas.geekcollection.ui.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    onAddManually: () -> Unit,
    onAddByPhoto: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel { LibraryViewModel(container.repository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val syncState by container.syncManager.state.collectAsStateWithLifecycle()
    // Toast once per finished sync run (summary or error).
    LaunchedEffect(syncState.lastSummary, syncState.lastError) {
        val s = syncState.lastSummary
        val e = syncState.lastError
        when {
            e != null -> Toast.makeText(context, context.getString(R.string.sync_failed, e), Toast.LENGTH_LONG).show()
            s != null && (s.pushed > 0 || s.pulled > 0 || s.removed > 0) ->
                Toast.makeText(context, context.getString(R.string.sync_result, s.pushed, s.pulled, s.removed), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.library_title))
                        if (state.loaded && state.totalCount > 0) {
                            Text(
                                stringResource(R.string.library_stats, state.totalCount, state.completedCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (container.syncManager.canSync) container.syncManager.syncNow()
                            else { Toast.makeText(context, R.string.sync_not_ready, Toast.LENGTH_SHORT).show(); onOpenSettings() }
                        },
                        enabled = !syncState.running,
                    ) {
                        if (syncState.running) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(
                            imageVector = if (container.syncManager.canSync) Icons.Filled.CloudSync else Icons.Filled.Cloud,
                            contentDescription = stringResource(R.string.sync_action),
                        )
                    }
                    IconButton(onClick = viewModel::toggleFavorites) {
                        Icon(
                            imageVector = if (filter.favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.library_filter_favorites),
                            tint = if (filter.favoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = null)
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SortOrder.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(sort)) },
                                    onClick = { viewModel.setSort(sort); sortMenuOpen = false },
                                    trailingIcon = if (filter.sort == sort) {
                                        { Icon(Icons.Filled.Check, contentDescription = null) }
                                    } else null,
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = onAddManually,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.library_add_manually))
                }
                ExtendedFloatingActionButton(
                    onClick = onAddByPhoto,
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_add_by_photo)) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true,
            )

            CategoryFilterRow(selected = filter.category, onSelect = viewModel::setCategory)
            ProgressFilterRow(selected = filter.progress, onSelect = viewModel::setProgress)

            when {
                !state.loaded -> Unit
                state.items.isEmpty() && state.totalCount == 0 -> EmptyLibrary()
                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.library_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ItemCard(item = item, onClick = { onOpenItem(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun sortLabel(sort: SortOrder): String = when (sort) {
    SortOrder.RECENT -> stringResource(R.string.library_sort_recent)
    SortOrder.TITLE -> stringResource(R.string.library_sort_title)
    SortOrder.COMPLETION -> stringResource(R.string.library_sort_completion)
}

@Composable
private fun CategoryFilterRow(selected: ItemCategory?, onSelect: (ItemCategory?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.library_filter_all)) },
            )
        }
        items(ItemCategory.entries) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category.label()) },
                leadingIcon = { Icon(category.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun progressFilterLabel(filter: ProgressFilter): String = when (filter) {
    ProgressFilter.BACKLOG -> "📥 " + stringResource(R.string.library_progress_backlog)
    ProgressFilter.IN_PROGRESS -> "▶️ " + stringResource(R.string.library_progress_in_progress)
    ProgressFilter.PAUSED -> "⏸️ " + stringResource(R.string.library_progress_paused)
    ProgressFilter.ABANDONED -> "⛔ " + stringResource(R.string.library_progress_abandoned)
    ProgressFilter.FINISHED -> "🏁 " + stringResource(R.string.library_progress_finished)
    ProgressFilter.PLATINUM -> "🏆 " + stringResource(R.string.library_progress_platinum)
}

@Composable
private fun ProgressFilterRow(selected: ProgressFilter?, onSelect: (ProgressFilter?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.library_progress_all)) },
            )
        }
        items(ProgressFilter.entries) { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(if (selected == filter) null else filter) },
                label = { Text(progressFilterLabel(filter)) },
            )
        }
    }
}

@Composable
private fun ItemCard(item: CollectionItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemCover(item = item, modifier = Modifier.size(width = 64.dp, height = 84.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.favorite) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                val subtitle = listOfNotNull(
                    item.category.label(),
                    item.platform,
                    item.releaseYear?.toString(),
                ).joinToString(" · ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.creator?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.backlog) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("📥 " + stringResource(R.string.flag_backlog)) },
                        modifier = Modifier.height(28.dp),
                    )
                } else if (item.category.supportsCompletion) {
                    Spacer(Modifier.height(8.dp))
                    CompletionBar(item = item)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
