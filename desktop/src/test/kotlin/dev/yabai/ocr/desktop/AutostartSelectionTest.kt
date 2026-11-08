package dev.yabai.ocr.desktop

import org.junit.Assert.assertSame
import org.junit.Test

class AutostartSelectionTest {

    private fun fake(enabled: Boolean = false): Autostart = object : Autostart {
        override fun setEnabled(enabled: Boolean) = Unit
        override fun isEnabled(): Boolean = enabled
    }

    @Test
    fun `linux selects the xdg implementation`() {
        val windows = fake()
        val linux = fake()
        val other = fake()

        assertSame(linux, autostartFor(OsFamily.LINUX, windows = windows, linux = linux, other = other))
    }

    @Test
    fun `windows selects the registry implementation`() {
        val windows = fake()
        val linux = fake()
        val other = fake()

        assertSame(windows, autostartFor(OsFamily.WINDOWS, windows = windows, linux = linux, other = other))
    }

    @Test
    fun `other systems select the no-op implementation`() {
        val windows = fake()
        val linux = fake()
        val other = fake()

        assertSame(other, autostartFor(OsFamily.MACOS, windows = windows, linux = linux, other = other))
    }
}
