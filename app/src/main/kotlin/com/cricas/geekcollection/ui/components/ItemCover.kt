package com.cricas.geekcollection.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.cricas.geekcollection.core.model.CollectionItem
import java.io.File

/**
 * Shows the item's picture: the local photo when present, otherwise the
 * remote cover, otherwise a category icon placeholder.
 */
@Composable
fun ItemCover(
    item: CollectionItem,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val model: Any? = item.localImagePath?.let { File(it) }?.takeIf { it.exists() } ?: item.coverUrl
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Placeholder(item)
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = item.title,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                error = { Placeholder(item) },
                loading = { Placeholder(item) },
            )
        }
    }
}

@Composable
private fun Placeholder(item: CollectionItem) {
    Icon(
        imageVector = item.category.icon(),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(36.dp),
    )
}
