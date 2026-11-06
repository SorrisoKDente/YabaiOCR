package dev.yabai.ocr

import android.content.Context
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatDelegate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppThemeTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val settings = AppSettings(context)
        settings.appTheme = AppThemeChoice.DARK
        settings.applyTheme()
    }

    @Test
    fun `defaults to DARK theme`() {
        val settings = AppSettings(context)
        assertEquals(AppThemeChoice.DARK, settings.appTheme)
    }

    @Test
    fun `persists LIGHT theme setting`() {
        val settings = AppSettings(context)
        settings.appTheme = AppThemeChoice.LIGHT

        val reloaded = AppSettings(context)
        assertEquals(AppThemeChoice.LIGHT, reloaded.appTheme)
    }

    @Test
    fun `persists SYSTEM theme setting`() {
        val settings = AppSettings(context)
        settings.appTheme = AppThemeChoice.SYSTEM

        val reloaded = AppSettings(context)
        assertEquals(AppThemeChoice.SYSTEM, reloaded.appTheme)
    }

    @Test
    fun `applyTheme sets AppCompatDelegate default night mode`() {
        val settings = AppSettings(context)

        settings.appTheme = AppThemeChoice.LIGHT
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())

        settings.appTheme = AppThemeChoice.DARK
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())

        settings.appTheme = AppThemeChoice.SYSTEM
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun `toggling theme from button updates appTheme and survives activity recreation`() {
        val settings = AppSettings(context)
        settings.appTheme = AppThemeChoice.DARK

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnThemeToggle = activity.findViewById<Button>(R.id.btnThemeToggle)
        btnThemeToggle.performClick()

        assertEquals(AppThemeChoice.LIGHT, settings.appTheme)

        val recreatedActivity = controller.recreate().get()
        assertEquals(AppThemeChoice.LIGHT, settings.appTheme)

        val recreatedBtn = recreatedActivity.findViewById<Button>(R.id.btnThemeToggle)
        assertEquals("☀️ Claro", recreatedBtn.text.toString())

        val themeGroup = recreatedActivity.findViewById<RadioGroup>(R.id.themeGroup)
        assertEquals(R.id.themeLight, themeGroup.checkedRadioButtonId)
    }
}

