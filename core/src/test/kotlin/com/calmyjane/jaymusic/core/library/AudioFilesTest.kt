package com.calmyjane.jaymusic.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFilesTest {

    @Test
    fun `recognises audio extensions case-insensitively`() {
        assertTrue(AudioFiles.isAudio("Song.MP3"))
        assertTrue(AudioFiles.isAudio("a.flac"))
        assertFalse(AudioFiles.isAudio("cover.jpg"))
        assertFalse(AudioFiles.isAudio("noextension"))
    }

    @Test
    fun `ignores synology system folders`() {
        assertTrue(AudioFiles.isIgnoredFolder("@eaDir"))
        assertTrue(AudioFiles.isIgnoredFolder("#recycle"))
        assertTrue(AudioFiles.isIgnoredFolder(".hidden"))
        assertFalse(AudioFiles.isIgnoredFolder("Pink Floyd"))
    }

    @Test
    fun `guesses tags from artist album track layout`() {
        val tags = AudioFiles.guessTags("/music/Pink Floyd/The Wall/03 - Another Brick.flac")
        assertEquals("Another Brick", tags.title)
        assertEquals("Pink Floyd", tags.artist)
        assertEquals("The Wall", tags.album)
    }

    @Test
    fun `guesses artist from file name with dash`() {
        val tags = AudioFiles.guessTags("/music/Artist - Title.mp3")
        assertEquals("Title", tags.title)
        assertEquals("Artist", tags.artist)
        assertNull(tags.album)
    }

    @Test
    fun `strips track numbers in different styles`() {
        assertEquals("Song", AudioFiles.guessTags("/m/x/01. Song.mp3").title)
        assertEquals("Song", AudioFiles.guessTags("/m/x/1-03 Song.mp3").title)
        assertEquals("Song", AudioFiles.guessTags("/m/x/7 Song.mp3").title)
        // A title that is only a number is kept.
        assertEquals("1999", AudioFiles.guessTags("/m/x/1999.mp3").title)
    }

    @Test
    fun `parent folder`() {
        assertEquals("/music/a", AudioFiles.parentFolder("/music/a/b.mp3"))
    }
}
