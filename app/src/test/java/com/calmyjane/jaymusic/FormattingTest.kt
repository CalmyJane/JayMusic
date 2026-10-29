package com.calmyjane.jaymusic

import com.calmyjane.jaymusic.ui.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun `formats durations`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("3:07", formatDuration(187_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }
}
