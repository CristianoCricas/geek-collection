package com.cricas.geekcollection.core

import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.model.ProgressStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    @Test
    fun `platinum forces full completion and finished status`() {
        val item = CollectionItem(title = "Bloodborne", category = ItemCategory.VIDEO_GAME, completionPercent = 70, platinum = true).normalized()
        assertEquals(100, item.completionPercent)
        assertEquals(ProgressStatus.FINISHED, item.progressStatus)
        assertTrue(item.platinum)
    }

    @Test
    fun `status and backlog survive for games, platinum only for video games`() {
        val board = CollectionItem(title = "Catan", category = ItemCategory.BOARD_GAME, progressStatus = ProgressStatus.PAUSED, backlog = true, platinum = true).normalized()
        assertEquals(ProgressStatus.PAUSED, board.progressStatus)
        assertTrue(board.backlog)
        assertFalse(board.platinum)
        assertEquals(0, board.completionPercent)
    }

    @Test
    fun `books have status but no backlog, figures have neither`() {
        val book = CollectionItem(title = "Dune", category = ItemCategory.BOOK, progressStatus = ProgressStatus.ABANDONED, backlog = true).normalized()
        assertEquals(ProgressStatus.ABANDONED, book.progressStatus)
        assertFalse(book.backlog)

        val figure = CollectionItem(title = "Iron Man", category = ItemCategory.ACTION_FIGURE, completionPercent = 50, progressStatus = ProgressStatus.FINISHED, backlog = true, platinum = true).normalized()
        assertNull(figure.completionPercent)
        assertEquals(ProgressStatus.IN_PROGRESS, figure.progressStatus)
        assertFalse(figure.backlog)
        assertFalse(figure.platinum)
    }

    @Test
    fun `unknown status name falls back to in progress`() {
        assertEquals(ProgressStatus.IN_PROGRESS, ProgressStatus.fromName("nope"))
        assertEquals(ProgressStatus.FINISHED, ProgressStatus.fromName("FINISHED"))
    }
}
