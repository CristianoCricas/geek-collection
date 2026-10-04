package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.ItemCategory
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Board games, via the BoardGameGeek XML API 2 (no key required). */
class BoardGameGeekProvider(private val http: HttpFetcher) : LookupProvider {

    override val id: String = "bgg"
    override val categories: Set<ItemCategory> = setOf(ItemCategory.BOARD_GAME)

    override suspend fun searchByTitle(title: String, limit: Int): List<LookupResult> {
        val searchXml = http.get(
            "https://boardgamegeek.com/xmlapi2/search?type=boardgame,boardgameexpansion&query=${title.urlEncode()}"
        )
        val ids = parseSearchIds(searchXml).take(limit)
        if (ids.isEmpty()) return emptyList()
        val detailsXml = http.get("https://boardgamegeek.com/xmlapi2/thing?id=${ids.joinToString(",")}")
        return parseThings(detailsXml)
    }

    internal fun parseSearchIds(xml: String): List<String> {
        val doc = parse(xml)
        val items = doc.getElementsByTagName("item")
        val exact = mutableListOf<String>()
        val others = mutableListOf<String>()
        for (i in 0 until items.length) {
            val el = items.item(i) as Element
            val gameId = el.getAttribute("id").takeIf { it.isNotBlank() } ?: continue
            val nameType = el.firstChild("name")?.getAttribute("type")
            if (nameType == "primary") exact += gameId else others += gameId
        }
        return (exact + others).distinct()
    }

    internal fun parseThings(xml: String): List<LookupResult> {
        val doc = parse(xml)
        val items = doc.getElementsByTagName("item")
        val results = mutableListOf<LookupResult>()
        for (i in 0 until items.length) {
            val el = items.item(i) as Element
            val gameId = el.getAttribute("id")
            val names = el.children("name")
            val name = (names.firstOrNull { it.getAttribute("type") == "primary" } ?: names.firstOrNull())
                ?.getAttribute("value") ?: continue
            val year = el.firstChild("yearpublished")?.getAttribute("value")?.toIntOrNull()
            val image = el.firstChild("image")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            val description = el.firstChild("description")?.textContent?.stripHtml()?.takeIf { it.isNotEmpty() }
            val links = el.children("link")
            val designers = links.filter { it.getAttribute("type") == "boardgamedesigner" }.map { it.getAttribute("value") }
            val publisher = links.firstOrNull { it.getAttribute("type") == "boardgamepublisher" }?.getAttribute("value")
            results += LookupResult(
                title = name,
                category = ItemCategory.BOARD_GAME,
                source = id,
                externalId = gameId,
                description = description,
                creator = designers.joinToString(", ").takeIf { it.isNotBlank() },
                publisher = publisher,
                releaseYear = year,
                coverUrl = image,
            )
        }
        return results
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isValidating = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newDocumentBuilder().parse(xml.byteInputStream())

    private fun Element.children(tag: String): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.tagName == tag) out += node
        }
        return out
    }

    private fun Element.firstChild(tag: String): Element? = children(tag).firstOrNull()
}
