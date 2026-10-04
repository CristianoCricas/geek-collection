package com.cricas.geekcollection.core.recognition

import com.cricas.geekcollection.core.model.ItemCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeuristicsTest {

    @Test
    fun `platform detector prefers the most specific alias`() {
        assertEquals("PlayStation 5", PlatformDetector.detect(listOf("ONLY ON PLAYSTATION 5", "PEGI 18"))?.name)
        assertEquals("Nintendo Switch", PlatformDetector.detect(listOf("Nintendo Switch", "The Legend of Zelda"))?.name)
        assertEquals("Xbox Series X|S", PlatformDetector.detect(listOf("XBOX SERIES X"))?.name)
        assertEquals("Nintendo DS", PlatformDetector.detect(listOf("Nintendo", "DS"))?.name)
        assertNull(PlatformDetector.detect(listOf("Catan", "3-4 jogadores")))
    }

    @Test
    fun `short aliases must be whole words`() {
        // "ds" inside "words" must not match Nintendo DS.
        assertNull(PlatformDetector.detect(listOf("Nice words")))
    }

    @Test
    fun `title guesser skips boilerplate and picks the title`() {
        val lines = listOf("PEGI 12", "THE LEGEND OF ZELDA", "Breath of the Wild", "Nintendo Switch", "045496590420", "www.nintendo.com")
        assertEquals("THE LEGEND OF ZELDA", TitleGuesser.guess(lines))
        val ranked = TitleGuesser.rank(lines)
        assertEquals(listOf("THE LEGEND OF ZELDA", "Breath of the Wild"), ranked)
    }

    @Test
    fun `title guesser returns null when nothing looks like a title`() {
        assertNull(TitleGuesser.guess(listOf("123456", "PEGI 3", "ab")))
    }

    @Test
    fun `category suggester uses isbn barcode first`() {
        val result = RecognitionResult(barcodes = listOf("9780140328721"), labels = listOf(RecognizedLabel("Toy", 0.9f)))
        assertEquals(ItemCategory.BOOK, CategorySuggester.suggest(result))
    }

    @Test
    fun `category suggester uses labels and platform text`() {
        assertEquals(
            ItemCategory.ACTION_FIGURE,
            CategorySuggester.suggest(RecognitionResult(labels = listOf(RecognizedLabel("Action figure", 0.8f), RecognizedLabel("Toy", 0.7f)))),
        )
        assertEquals(
            ItemCategory.VIDEO_GAME,
            CategorySuggester.suggest(RecognitionResult(textLines = listOf("PlayStation 4", "God of War"))),
        )
        assertEquals(
            ItemCategory.ACCESSORY,
            CategorySuggester.suggest(RecognitionResult(textLines = listOf("Xbox One"), labels = listOf(RecognizedLabel("Game controller", 0.9f)))),
        )
        assertEquals(
            ItemCategory.BOARD_GAME,
            CategorySuggester.suggest(RecognitionResult(textLines = listOf("CATAN", "3-4 jogadores", "75 minutos"))),
        )
        assertNull(CategorySuggester.suggest(RecognitionResult()))
    }
}
