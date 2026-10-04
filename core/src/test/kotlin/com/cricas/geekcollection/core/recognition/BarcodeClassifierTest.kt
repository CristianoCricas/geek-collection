package com.cricas.geekcollection.core.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeClassifierTest {

    @Test
    fun `detects isbn13`() {
        assertTrue(BarcodeClassifier.isIsbn("9780140328721"))
        assertEquals(BarcodeKind.ISBN, BarcodeClassifier.classify("978-0-14-032872-1"))
    }

    @Test
    fun `converts isbn10 to isbn13`() {
        assertEquals("9780140328721", BarcodeClassifier.toIsbn13("0140328726"))
        assertEquals("9780306406157", BarcodeClassifier.toIsbn13("0-306-40615-2"))
    }

    @Test
    fun `rejects invalid check digit`() {
        assertFalse(BarcodeClassifier.isIsbn("9780140328722"))
        assertNull(BarcodeClassifier.toIsbn13("0140328722"))
    }

    @Test
    fun `classifies product ean as upc`() {
        assertEquals(BarcodeKind.EAN_UPC, BarcodeClassifier.classify("4002515289693"))
        assertEquals(BarcodeKind.EAN_UPC, BarcodeClassifier.classify("045496590420"))
        assertEquals(BarcodeKind.UNKNOWN, BarcodeClassifier.classify("https://example.com"))
    }
}
