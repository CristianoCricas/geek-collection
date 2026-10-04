package com.cricas.geekcollection.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.cricas.geekcollection.R
import com.cricas.geekcollection.core.model.ItemCategory

@Composable
fun ItemCategory.label(): String = stringResource(labelRes())

fun ItemCategory.labelRes(): Int = when (this) {
    ItemCategory.BOARD_GAME -> R.string.category_board_game
    ItemCategory.VIDEO_GAME -> R.string.category_video_game
    ItemCategory.CONSOLE -> R.string.category_console
    ItemCategory.ACCESSORY -> R.string.category_accessory
    ItemCategory.BOOK -> R.string.category_book
    ItemCategory.COMIC -> R.string.category_comic
    ItemCategory.ACTION_FIGURE -> R.string.category_action_figure
    ItemCategory.COLLECTIBLE -> R.string.category_collectible
    ItemCategory.OTHER -> R.string.category_other
}

fun ItemCategory.icon(): ImageVector = when (this) {
    ItemCategory.BOARD_GAME -> Icons.Filled.Casino
    ItemCategory.VIDEO_GAME -> Icons.Filled.SportsEsports
    ItemCategory.CONSOLE -> Icons.Filled.Tv
    ItemCategory.ACCESSORY -> Icons.Filled.Headset
    ItemCategory.BOOK -> Icons.Filled.MenuBook
    ItemCategory.COMIC -> Icons.Filled.AutoStories
    ItemCategory.ACTION_FIGURE -> Icons.Filled.SmartToy
    ItemCategory.COLLECTIBLE -> Icons.Filled.Star
    ItemCategory.OTHER -> Icons.Filled.Category
}

/** Label for the "creator" field, which changes meaning per category. */
fun ItemCategory.creatorLabelRes(): Int = when (this) {
    ItemCategory.BOOK, ItemCategory.COMIC -> R.string.field_creator_author
    ItemCategory.VIDEO_GAME -> R.string.field_creator_developer
    ItemCategory.BOARD_GAME -> R.string.field_creator_designer
    else -> R.string.field_creator_brand
}
