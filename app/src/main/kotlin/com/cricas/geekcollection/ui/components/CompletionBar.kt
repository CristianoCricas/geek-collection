package com.cricas.geekcollection.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cricas.geekcollection.R
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ProgressStatus

/** One-line progress summary: "Platinado", "História finalizada · 80%", "35% concluído"... */
@Composable
fun progressSummary(item: CollectionItem): String {
    val pct = item.completionOrZero
    return when {
        item.platinum -> "🏆 " + stringResource(R.string.flag_platinum)
        item.progressStatus == ProgressStatus.IN_PROGRESS ->
            if (pct >= 100) stringResource(R.string.detail_completed) else stringResource(R.string.field_completion_value, pct)
        else -> stringResource(R.string.status_with_percent, item.progressStatus.label(item.category), pct)
    }
}

@Composable
fun CompletionBar(item: CollectionItem, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val pct = item.completionOrZero
    val color = when {
        item.platinum -> Color(0xFF9FB3C8)
        item.progressStatus == ProgressStatus.FINISHED || pct >= 100 -> MaterialTheme.colorScheme.tertiary
        item.progressStatus == ProgressStatus.PAUSED -> Color(0xFFE0A800)
        item.progressStatus == ProgressStatus.ABANDONED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier = modifier) {
        if (showLabel) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    text = progressSummary(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
