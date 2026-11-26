package dev.yabai.ocr

import android.view.View
import android.view.ViewParent
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R56: the settings screen is split into tabs, like the desktop one.
 *
 * The screen used to be one long scroll with nine sections — language, the start card, permissions,
 * shortcuts, engine, behaviour, dictionaries, translation, AI, updates and donations — so the subject
 * a reader came for could be a screen and a half away, and every visit started from the top.
 *
 * What this protects: the strip has one tab per page, the tabs are named in the interface language,
 * exactly one page is laid out at a time (a second visible page would double the scroll and show the
 * same setting twice), a newly opened page starts at its beginning, and every setting that existed
 * still lives inside its own tab. The last one is the guard against a section being dropped while the
 * layout is moved around: an id that no longer resolves fails as loudly as one filed under the
 * wrong tab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class SettingsTabsTest {

    private val pages = listOf(
        R.id.pageGeneral,
        R.id.pageReading,
        R.id.pageDictionaries,
        R.id.pageAbout,
    )

    private val labels = listOf(
        R.string.tab_general,
        R.string.tab_reading,
        R.string.tab_dictionaries,
        R.string.tab_about,
    )

    @Test
    fun `one tab per page and only the selected page is laid out`() {
        val activity = open()
        val tabs = activity.findViewById<TabLayout>(R.id.settingsTabs)

        assertEquals("the strip no longer has one tab per page", pages.size, tabs.tabCount)
        labels.forEachIndexed { index, id ->
            assertEquals(
                "tab $index is not the «${activity.getString(id)}» one",
                activity.getString(id),
                tabs.getTabAt(index)?.text.toString(),
            )
        }

        assertEquals("the screen no longer opens on the first tab", 0, tabs.selectedTabPosition)
        assertOnlyPageShown(activity, 0)

        pages.indices.forEach { index ->
            tabs.getTabAt(index)!!.select()
            assertEquals("selecting tab $index did not select it", index, tabs.selectedTabPosition)
            assertOnlyPageShown(activity, index)
        }
    }

    @Test
    fun `a page opens at its beginning`() {
        val activity = open()
        val tabs = activity.findViewById<TabLayout>(R.id.settingsTabs)
        val scroll = activity.findViewById<ScrollView>(R.id.settingsScroll)

        tabs.getTabAt(1)!!.select()
        scroll.scrollTo(0, 240)
        assertEquals("the page did not scroll", 240, scroll.scrollY)

        tabs.getTabAt(2)!!.select()
        assertEquals(
            "the new page kept the previous page's scroll offset, so it opens halfway down",
            0,
            scroll.scrollY,
        )
    }

    @Test
    fun `every setting kept its tab`() {
        val activity = open()

        assertInside(activity, R.id.pageGeneral, R.id.langGroup, R.id.btnStart, R.id.btnStop,
            R.id.overlayStatus, R.id.btnNotificationPermission)
        assertInside(activity, R.id.pageReading, R.id.engineGroup, R.id.checkFurigana,
            R.id.groupPickerReading, R.id.checkTranslate, R.id.checkAskAi, R.id.editAiPrompt)
        assertInside(activity, R.id.pageDictionaries, R.id.dictionarySummary,
            R.id.btnManageDictionaries, R.id.checkDictionaryKeepLast, R.id.checkStripReadings)
        assertInside(activity, R.id.pageAbout, R.id.updateVersionText, R.id.btnCheckAppUpdate,
            R.id.btnDonateKofi, R.id.btnShowLicense)
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun open(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun assertOnlyPageShown(activity: MainActivity, selected: Int) {
        pages.forEachIndexed { position, id ->
            val page = activity.findViewById<View>(id)
            assertNotNull("page $position is gone from the layout", page)
            assertEquals(
                "page $position is ${if (position == selected) "hidden" else "showing"} " +
                    "while tab $selected is selected",
                if (position == selected) View.VISIBLE else View.GONE,
                page.visibility,
            )
            assertTrue(
                "page $position has no content left in it",
                page is LinearLayout && page.childCount > 0,
            )
        }
    }

    private fun assertInside(activity: MainActivity, pageId: Int, vararg ids: Int) {
        val page = activity.findViewById<View>(pageId)
        ids.forEach { id ->
            val view = activity.findViewById<View>(id)
            assertNotNull("a setting disappeared from the screen: $id", view)
            assertTrue(
                "the setting ${activity.resources.getResourceEntryName(id)} is not inside " +
                    activity.resources.getResourceEntryName(pageId),
                isInside(view, page),
            )
        }
    }

    private fun isInside(view: View, ancestor: View): Boolean {
        var current: ViewParent? = view.parent
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }
}
