package com.cricas.geekcollection.core.recognition

/**
 * Guesses the product title from OCR lines of a box or cover.
 * Lines are scored by how much they look like a title: mostly letters,
 * reasonably long, not boilerplate such as age ratings or platform names.
 */
object TitleGuesser {

    private val boilerplate = listOf(
        "pegi", "esrb", "classificação", "classificacao", "indicativa", "anos", "years",
        "only on", "exclusivo", "edition", "edição", "edicao", "blu-ray", "dvd", "disc", "disco",
        "playstation", "xbox", "nintendo", "switch", "ps4", "ps5", "wii", "sega", "pc dvd",
        "jogadores", "players", "idade", "ages", "contém", "contains", "made in", "fabricado",
        "www.", ".com", "http", "isbn", "barcode", "código", "codigo", "bestseller", "best seller",
        "autor", "author", "editora", "publisher", "tradução", "volume", "vol.",
    )

    fun guess(lines: List<String>): String? {
        return rank(lines).firstOrNull()
    }

    /** Returns candidate titles ordered from the most to the least likely. */
    fun rank(lines: List<String>): List<String> {
        return lines
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length >= 3 }
            .map { it to score(it) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.lowercase() }
            .take(5)
    }

    private fun score(line: String): Double {
        val letters = line.count { it.isLetter() }
        val digits = line.count { it.isDigit() }
        val total = line.length
        if (letters < 3) return 0.0
        val alphaRatio = letters.toDouble() / total
        if (alphaRatio < 0.5) return 0.0
        val lower = line.lowercase()
        if (boilerplate.any { lower.contains(it) }) return 0.0
        if (digits > letters) return 0.0

        var score = letters.toDouble().coerceAtMost(30.0)
        // Titles on covers are often printed in capitals.
        val upperRatio = line.count { it.isUpperCase() }.toDouble() / letters
        if (upperRatio > 0.7) score += 6
        // Very long lines are probably description text.
        if (total > 45) score -= (total - 45) * 0.5
        // Title-case words are a good sign.
        val words = line.split(' ')
        if (words.size in 1..7 && words.all { w -> w.firstOrNull()?.isUpperCase() == true || w.length <= 3 }) score += 4
        return score
    }
}
