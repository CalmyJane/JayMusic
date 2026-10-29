package com.calmyjane.jaymusic.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRulesTest {

    private val rules = FolderRules(listOf("/music/Audiobooks/", "music/Podcasts"))

    @Test
    fun `excludes folder and everything below it`() {
        assertTrue(rules.isExcluded("/music/Audiobooks/Book/01.mp3"))
        assertTrue(rules.isExcluded("/music/Podcasts"))
        assertEquals("/music/Podcasts", rules.excludedBy("/music/Podcasts/x.mp3"))
    }

    @Test
    fun `does not exclude siblings with the same prefix`() {
        assertFalse(rules.isExcluded("/music/Audiobooks2/x.mp3"))
        assertFalse(rules.isExcluded("/music/Rock/x.mp3"))
    }

    @Test
    fun `empty rules exclude nothing`() {
        assertFalse(FolderRules.NONE.isExcluded("/music/a.mp3"))
    }
}
