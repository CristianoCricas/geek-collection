package com.cricas.geekcollection.core.model

/**
 * Categories an item of the collection can belong to.
 *
 * @property supportsPlatform whether the item can be tied to a gaming platform
 *   (video games, consoles and accessories).
 * @property supportsCompletion whether the item has a completion percentage and a
 *   progress status (board games, video games, books and comics).
 * @property supportsBacklog whether the item can be flagged as "backlog" (games in general).
 * @property supportsPlatinum whether the item can be flagged as "platinado" (video games).
 */
enum class ItemCategory(
    val supportsPlatform: Boolean,
    val supportsCompletion: Boolean,
    val supportsBacklog: Boolean = false,
    val supportsPlatinum: Boolean = false,
) {
    BOARD_GAME(supportsPlatform = false, supportsCompletion = true, supportsBacklog = true),
    VIDEO_GAME(supportsPlatform = true, supportsCompletion = true, supportsBacklog = true, supportsPlatinum = true),
    CONSOLE(supportsPlatform = true, supportsCompletion = false),
    ACCESSORY(supportsPlatform = true, supportsCompletion = false),
    BOOK(supportsPlatform = false, supportsCompletion = true),
    COMIC(supportsPlatform = false, supportsCompletion = true),
    ACTION_FIGURE(supportsPlatform = false, supportsCompletion = false),
    COLLECTIBLE(supportsPlatform = false, supportsCompletion = false),
    OTHER(supportsPlatform = false, supportsCompletion = false);

    companion object {
        fun fromName(name: String?): ItemCategory =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
