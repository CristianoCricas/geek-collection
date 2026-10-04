package com.cricas.geekcollection.core.recognition

enum class BarcodeKind { ISBN, EAN_UPC, UNKNOWN }

/** Classifies raw barcode values so the right online source can be queried. */
object BarcodeClassifier {

    fun normalize(raw: String): String = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()

    fun classify(raw: String): BarcodeKind {
        val value = normalize(raw)
        return when {
            isIsbn(value) -> BarcodeKind.ISBN
            value.length in 8..14 && value.all { it.isDigit() } -> BarcodeKind.EAN_UPC
            else -> BarcodeKind.UNKNOWN
        }
    }

    fun isIsbn(raw: String): Boolean {
        val value = normalize(raw)
        return when (value.length) {
            10 -> isValidIsbn10(value)
            13 -> (value.startsWith("978") || value.startsWith("979")) && isValidEan13(value)
            else -> false
        }
    }

    /** Returns the ISBN-13 form of a valid ISBN, or null when it is not an ISBN. */
    fun toIsbn13(raw: String): String? {
        val value = normalize(raw)
        return when {
            value.length == 13 && isIsbn(value) -> value
            value.length == 10 && isValidIsbn10(value) -> {
                val base = "978" + value.substring(0, 9)
                base + ean13CheckDigit(base)
            }
            else -> null
        }
    }

    private fun isValidIsbn10(value: String): Boolean {
        if (value.length != 10) return false
        var sum = 0
        for (i in 0 until 10) {
            val c = value[i]
            val digit = when {
                c.isDigit() -> c - '0'
                c == 'X' && i == 9 -> 10
                else -> return false
            }
            sum += digit * (10 - i)
        }
        return sum % 11 == 0
    }

    private fun isValidEan13(value: String): Boolean {
        if (value.length != 13 || !value.all { it.isDigit() }) return false
        return ean13CheckDigit(value.substring(0, 12)) == value[12]
    }

    private fun ean13CheckDigit(first12: String): Char {
        var sum = 0
        for (i in 0 until 12) {
            val d = first12[i] - '0'
            sum += if (i % 2 == 0) d else d * 3
        }
        return ('0' + (10 - sum % 10) % 10)
    }
}
