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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cricas.geekcollection.R

@Composable
fun CompletionBar(percent: Int, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val clamped = percent.coerceIn(0, 100)
    Column(modifier = modifier) {
        if (showLabel) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    text = if (clamped >= 100) stringResource(R.string.detail_completed)
                    else stringResource(R.string.field_completion_value, clamped),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { clamped / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (clamped >= 100) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
