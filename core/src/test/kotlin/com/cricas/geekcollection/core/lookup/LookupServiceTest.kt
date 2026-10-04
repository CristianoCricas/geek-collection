package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.recognition.RecognitionResult
import com.cricas.geekcollection.core.recognition.RecognizedLabel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubProvider(
    override val id: String,
    override val categories: Set<ItemCategory>,
    private val byTitle: (String) -> List<LookupResult> = { emptyList() },
    private val byBarcode: (String) -> List<LookupResult> = { emptyList() },
    private val fail: Boolean = false,
) : LookupProvider {
    val titleQueries = mutableListOf<String>()
    override suspend fun searchByTitle(title: String, limit: Int): List<LookupResult> {
        if (fail) throw IllegalStateException("boom")
        titleQueries += title
        return byTitle(title)
    }
    override suspend fun searchByBarcode(barcode: String): List<LookupResult> = byBarcode(barcode)
}

class LookupServiceTest {

    private fun result(source: String, title: String, category: ItemCategory? = null, id: String? = null) =
        LookupResult(title = title, category = category, source = source, externalId = id)

    @Test
    fun `isbn barcode goes to open library and suggests book`() = runTest {
        val openLibrary = StubProvider("openlibrary", setOf(ItemCategory.BOOK), byBarcode = { listOf(result("openlibrary", "Dune", ItemCategory.BOOK, "1")) })
        val bgg = StubProvider("bgg", setOf(ItemCategory.BOARD_GAME))
        val service = LookupService(listOf(openLibrary, bgg))

        val outcome = service.lookup(RecognitionResult(barcodes = listOf("9780441172719"), textLines = listOf("DUNE", "Frank Herbert")))

        assertEquals(ItemCategory.BOOK, outcome.suggestedCategory)
        assertEquals("9780441172719", outcome.barcode)
        assertEquals("Dune", outcome.candidates.single().title)
        // Title search still ran to reach MIN_RESULTS, only on book-capable + generic providers.
        assertTrue(bgg.titleQueries.isEmpty())
        assertTrue(openLibrary.titleQueries.isNotEmpty())
    }

    @Test
    fun `video game text uses platform hint and generic providers`() = runTest {
        val wiki = StubProvider("wikipedia", emptySet(), byTitle = { listOf(result("wikipedia", "God of War (2018)")) })
        val rawg = StubProvider("rawg", setOf(ItemCategory.VIDEO_GAME), byTitle = { listOf(result("rawg", "God of War", ItemCategory.VIDEO_GAME, "58175")) })
        val bgg = StubProvider("bgg", setOf(ItemCategory.BOARD_GAME), byTitle = { listOf(result("bgg", "God of War: The Card Game", ItemCategory.BOARD_GAME)) })
        val service = LookupService(listOf(wiki, rawg, bgg))

        val outcome = service.lookup(RecognitionResult(textLines = listOf("GOD OF WAR", "Only on PlayStation 4", "PEGI 18")))

        assertEquals(ItemCategory.VIDEO_GAME, outcome.suggestedCategory)
        assertEquals("PlayStation 4", outcome.suggestedPlatform)
        assertEquals("GOD OF WAR", outcome.titleGuess)
        assertEquals(setOf("rawg", "wikipedia"), outcome.candidates.map { it.source }.toSet())
        // Hints are applied to the generic result.
        val wikiResult = outcome.candidates.first { it.source == "wikipedia" }
        assertEquals(ItemCategory.VIDEO_GAME, wikiResult.category)
        assertEquals("PlayStation 4", wikiResult.platform)
        assertTrue(bgg.titleQueries.isEmpty())
    }

    @Test
    fun `failing provider is reported but does not break the lookup`() = runTest {
        val broken = StubProvider("bgg", setOf(ItemCategory.BOARD_GAME), fail = true)
        val wiki = StubProvider("wikipedia", emptySet(), byTitle = { listOf(result("wikipedia", "Catan")) })
        val service = LookupService(listOf(broken, wiki))

        val outcome = service.searchByTitle("Catan", ItemCategory.BOARD_GAME)

        assertEquals(listOf("Catan"), outcome.candidates.map { it.title })
        assertEquals(1, outcome.errors.size)
        assertTrue(outcome.errors.first().startsWith("bgg:"))
    }

    @Test
    fun `unknown category searches every title provider`() = runTest {
        val labels = listOf(RecognizedLabel("Furniture", 0.5f))
        val wiki = StubProvider("wikipedia", emptySet(), byTitle = { listOf(result("wikipedia", "Thing")) })
        val upc = StubProvider("upcitemdb", emptySet(), byTitle = { error("should not be called for titles") })
        val service = LookupService(listOf(wiki, upc))

        val outcome = service.lookup(RecognitionResult(textLines = listOf("Some Product Name"), labels = labels))

        assertEquals(null, outcome.suggestedCategory)
        assertEquals("Thing", outcome.candidates.single().title)
    }
}
