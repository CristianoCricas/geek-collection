package com.cricas.geekcollection.core.recognition

import com.cricas.geekcollection.core.model.ItemCategory

/**
 * Suggests the most likely [ItemCategory] from image labels, recognized text
 * and barcode kind.
 */
object CategorySuggester {

    private val labelMap: List<Pair<List<String>, ItemCategory>> = listOf(
        listOf("book", "paper", "novel", "publication", "textbook") to ItemCategory.BOOK,
        listOf("comic", "comics", "manga", "cartoon", "illustration") to ItemCategory.COMIC,
        listOf("toy", "figurine", "action figure", "doll", "statue", "sculpture", "robot", "superhero") to ItemCategory.ACTION_FIGURE,
        listOf("board game", "dice", "chess", "tabletop", "card game", "playing card", "games", "puzzle", "poker") to ItemCategory.BOARD_GAME,
        listOf("video game", "videogame", "game controller", "gamepad", "joystick", "game console", "console") to ItemCategory.VIDEO_GAME,
        listOf("electronics", "electronic device", "gadget", "cable", "headphones", "controller") to ItemCategory.ACCESSORY,
        listOf("plush", "stuffed toy", "mug", "poster", "keychain", "pin", "collectible", "model") to ItemCategory.COLLECTIBLE,
    )

    fun suggest(result: RecognitionResult): ItemCategory? {
        // Barcode kind is the strongest signal we have.
        if (result.barcodes.any { BarcodeClassifier.classify(it) == BarcodeKind.ISBN }) {
            return ItemCategory.BOOK
        }

        val scores = mutableMapOf<ItemCategory, Double>()
        for (label in result.labels) {
            val text = label.text.lowercase()
            for ((keywords, category) in labelMap) {
                if (keywords.any { text == it || text.contains(it) }) {
                    scores[category] = (scores[category] ?: 0.0) + label.confidence
                }
            }
        }

        val platform = PlatformDetector.detect(result.textLines)
        if (platform != null) {
            val labelText = result.labels.joinToString(" ") { it.text.lowercase() }
            val category = when {
                labelText.contains("controller") || labelText.contains("gamepad") || labelText.contains("cable") -> ItemCategory.ACCESSORY
                labelText.contains("console") || labelText.contains("electronic") -> ItemCategory.CONSOLE
                else -> ItemCategory.VIDEO_GAME
            }
            scores[category] = (scores[category] ?: 0.0) + 1.0
        }

        val text = result.textLines.joinToString(" ").lowercase()
        if (text.contains("jogadores") || text.contains("players") || text.contains("minutos") || text.contains("min.")) {
            scores[ItemCategory.BOARD_GAME] = (scores[ItemCategory.BOARD_GAME] ?: 0.0) + 0.8
        }
        if (text.contains("isbn") || text.contains("editora") || text.contains("romance")) {
            scores[ItemCategory.BOOK] = (scores[ItemCategory.BOOK] ?: 0.0) + 0.8
        }
        if (text.contains("action figure") || text.contains("figura de ação") || text.contains("figure")) {
            scores[ItemCategory.ACTION_FIGURE] = (scores[ItemCategory.ACTION_FIGURE] ?: 0.0) + 0.8
        }

        return scores.maxByOrNull { it.value }?.key
    }
}
