package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.ItemCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeHttp(private val responses: Map<String, String>) : HttpFetcher {
    val requested = mutableListOf<String>()
    override suspend fun get(url: String, headers: Map<String, String>): String {
        requested += url
        val key = responses.keys.firstOrNull { url.contains(it) } ?: error("Unexpected URL: $url")
        return responses.getValue(key)
    }
}

class ProvidersTest {

    @Test
    fun `open library parses isbn response`() = runTest {
        val body = """
            {"ISBN:9780140328721": {"title": "Fantastic Mr. Fox", "key": "/books/OL7353617M",
              "authors": [{"name": "Roald Dahl"}], "publishers": [{"name": "Puffin"}],
              "publish_date": "October 1, 1988", "cover": {"large": "https://covers.openlibrary.org/b/id/8739161-L.jpg"}}}
        """.trimIndent()
        val provider = OpenLibraryProvider(FakeHttp(mapOf("api/books" to body)))
        val results = provider.searchByBarcode("0140328726")
        assertEquals(1, results.size)
        val r = results.first()
        assertEquals("Fantastic Mr. Fox", r.title)
        assertEquals("Roald Dahl", r.creator)
        assertEquals("Puffin", r.publisher)
        assertEquals(1988, r.releaseYear)
        assertEquals("9780140328721", r.barcode)
        assertEquals(ItemCategory.BOOK, r.category)
    }

    @Test
    fun `open library parses title search`() = runTest {
        val body = """{"docs": [{"key": "/works/OL45804W", "title": "Dune", "author_name": ["Frank Herbert"],
            "first_publish_year": 1965, "cover_i": 12345, "publisher": ["Chilton"], "isbn": ["0441172717", "9780441172719"]}]}"""
        val results = OpenLibraryProvider(FakeHttp(mapOf("search.json" to body))).searchByTitle("Dune")
        assertEquals("Dune", results.single().title)
        assertEquals("https://covers.openlibrary.org/b/id/12345-L.jpg", results.single().coverUrl)
        assertEquals("9780441172719", results.single().barcode)
    }

    @Test
    fun `bgg parses search and thing responses`() = runTest {
        val search = """<?xml version="1.0" encoding="utf-8"?>
            <items total="2">
              <item type="boardgame" id="13"><name type="primary" value="Catan"/><yearpublished value="1995"/></item>
              <item type="boardgame" id="926"><name type="alternate" value="Catan: Seafarers"/></item>
            </items>"""
        val thing = """<?xml version="1.0" encoding="utf-8"?>
            <items>
              <item type="boardgame" id="13">
                <thumbnail>https://cf.geekdo-images.com/thumb.jpg</thumbnail>
                <image>https://cf.geekdo-images.com/catan.jpg</image>
                <name type="primary" sortindex="1" value="CATAN"/>
                <name type="alternate" sortindex="1" value="Os Colonizadores de Catan"/>
                <description>In CATAN (formerly The Settlers of Catan), players try to be the dominant force.&amp;#10;&amp;#10;Second paragraph &amp;quot;quoted&amp;quot;.</description>
                <yearpublished value="1995"/>
                <link type="boardgamedesigner" id="11" value="Klaus Teuber"/>
                <link type="boardgamepublisher" id="37" value="KOSMOS"/>
                <link type="boardgamepublisher" id="38" value="Devir"/>
              </item>
            </items>"""
        val http = FakeHttp(mapOf("xmlapi2/search" to search, "xmlapi2/thing" to thing))
        val results = BoardGameGeekProvider(http).searchByTitle("Catan")
        assertTrue(http.requested[1].endsWith("id=13,926"))
        val r = results.single()
        assertEquals("CATAN", r.title)
        assertEquals("13", r.externalId)
        assertEquals(1995, r.releaseYear)
        assertEquals("Klaus Teuber", r.creator)
        assertEquals("KOSMOS", r.publisher)
        assertEquals("https://cf.geekdo-images.com/catan.jpg", r.coverUrl)
        assertTrue(r.description!!.startsWith("In CATAN"))
        assertTrue(r.description!!.contains("\"quoted\""))
    }

    @Test
    fun `rawg parses games and prefers the requested platform`() = runTest {
        val body = """{"results": [{"id": 3328, "name": "The Witcher 3: Wild Hunt", "released": "2015-05-18",
            "background_image": "https://media.rawg.io/w3.jpg",
            "platforms": [{"platform": {"name": "PC"}}, {"platform": {"name": "PlayStation 4"}}, {"platform": {"name": "Nintendo Switch"}}]}]}"""
        val provider = RawgProvider(FakeHttp(mapOf("api.rawg.io" to body)), { "key" }, { "Nintendo Switch" })
        val r = provider.searchByTitle("witcher").single()
        assertEquals("The Witcher 3: Wild Hunt", r.title)
        assertEquals("Nintendo Switch", r.platform)
        assertEquals(2015, r.releaseYear)
        assertEquals("3328", r.externalId)
    }

    @Test
    fun `rawg without key returns nothing and does not call the network`() = runTest {
        val http = FakeHttp(emptyMap())
        val results = RawgProvider(http, { null }).searchByTitle("zelda")
        assertTrue(results.isEmpty())
        assertTrue(http.requested.isEmpty())
    }

    @Test
    fun `wikipedia parses pages ordered by index`() = runTest {
        val body = """{"query": {"pages": {
            "2": {"pageid": 2, "index": 2, "title": "Mega Drive", "extract": "O Mega Drive é um console lançado em 1988."},
            "1": {"pageid": 1, "index": 1, "title": "Sega Genesis", "extract": "Console da Sega.", "thumbnail": {"source": "https://upload.wikimedia.org/g.jpg"}}}}}"""
        val results = WikipediaProvider(FakeHttp(mapOf("wikipedia.org" to body))).searchByTitle("mega drive")
        assertEquals(listOf("Sega Genesis", "Mega Drive"), results.map { it.title })
        assertEquals("https://upload.wikimedia.org/g.jpg", results[0].coverUrl)
        assertEquals(1988, results[1].releaseYear)
    }

    @Test
    fun `upcitemdb parses items and guesses category`() = runTest {
        val body = """{"code": "OK", "items": [{"ean": "0045496590420", "title": "The Legend of Zelda: Breath of the Wild - Nintendo Switch",
            "brand": "Nintendo", "category": "Media > Video Games", "images": ["https://i5.walmartimages.com/zelda.jpg"]}]}"""
        val results = UpcItemDbProvider(FakeHttp(mapOf("upcitemdb" to body))).searchByBarcode("045496590420")
        val r = results.single()
        assertEquals(ItemCategory.VIDEO_GAME, r.category)
        assertEquals("Nintendo", r.creator)
        assertEquals("045496590420", r.barcode)
        assertEquals("https://i5.walmartimages.com/zelda.jpg", r.coverUrl)
    }
}
