package com.cricas.geekcollection.core.recognition

import com.cricas.geekcollection.core.model.Platform
import com.cricas.geekcollection.core.model.Platforms

/** Detects a gaming platform mentioned in recognized text (box art, labels, OCR). */
object PlatformDetector {

    private data class Candidate(val platform: Platform, val alias: String)

    private val candidates: List<Candidate> = Platforms.all
        .flatMap { p -> (listOf(p.name) + p.aliases).map { Candidate(p, it) } }
        // Longer aliases first so "PlayStation 5" wins over "PlayStation".
        .sortedByDescending { it.alias.length }

    fun detect(lines: List<String>): Platform? {
        val text = lines.joinToString(" ").lowercase()
        if (text.isBlank()) return null
        for (candidate in candidates) {
            val alias = candidate.alias.lowercase()
            if (alias.length <= 3 && candidate.platform.name != alias) {
                // Short aliases ("GB", "DS") must appear as whole words to avoid noise.
                if (Regex("(?<![a-z0-9])${Regex.escape(alias)}(?![a-z0-9])").containsMatchIn(text)) {
                    return candidate.platform
                }
            } else if (text.contains(alias)) {
                return candidate.platform
            }
        }
        return null
    }
}
