package com.cricas.geekcollection.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cricas.geekcollection.R
import com.cricas.geekcollection.core.lookup.LookupResult
import com.cricas.geekcollection.ui.edit.OnlineSearchState

/** Bottom sheet listing online candidates; shared by the edit form and the scan flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupResultsSheet(
    state: OnlineSearchState,
    onDismiss: () -> Unit,
    onPick: (LookupResult) -> Unit,
    onRetry: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.scan_results_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            when (state) {
                OnlineSearchState.Hidden -> Unit
                OnlineSearchState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.scan_searching))
                    }
                }
                is OnlineSearchState.Error -> Column {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                is OnlineSearchState.Results -> {
                    if (state.outcome.candidates.isEmpty()) {
                        Text(stringResource(R.string.scan_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.outcome.candidates) { result ->
                                LookupResultRow(result = result, onClick = { onPick(result) })
                            }
                        }
                    }
                    if (state.outcome.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.scan_provider_errors, state.outcome.errors.joinToString("; ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LookupResultRow(result: LookupResult, onClick: () -> Unit) {
    val preview = result.toItem()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover(item = preview, modifier = Modifier.size(width = 48.dp, height = 64.dp), cornerRadius = 8)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                result.category?.let { preview.category.label() },
                result.platform,
                result.releaseYear?.toString(),
                result.creator,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                result.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
