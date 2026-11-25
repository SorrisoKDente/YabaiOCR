package dev.yabai.ocr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity

class HelpActivity : AppCompatActivity() {

    /** One row of the button reference: a vector icon or a text glyph, its name and what it does. */
    private data class HelpButton(
        @StringRes val title: Int,
        @StringRes val description: Int,
        @DrawableRes val icon: Int = 0,
        val glyph: String = "",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings(this).applyTheme(this)
        setContentView(R.layout.activity_help)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        addHelpButtons()
    }

    private fun addHelpButtons() {
        val list = findViewById<LinearLayout>(R.id.helpButtonList)
        val inflater = LayoutInflater.from(this)
        for (button in HELP_BUTTONS) {
            val row = inflater.inflate(R.layout.item_help_button, list, false)
            row.findViewById<TextView>(R.id.helpButtonTitle).setText(button.title)
            row.findViewById<TextView>(R.id.helpButtonDesc).setText(button.description)
            if (button.glyph.isEmpty()) {
                row.findViewById<ImageView>(R.id.helpButtonIcon).setImageResource(button.icon)
            } else {
                row.findViewById<ImageView>(R.id.helpButtonIcon).visibility = View.GONE
                row.findViewById<TextView>(R.id.helpButtonGlyph).apply {
                    text = button.glyph
                    visibility = View.VISIBLE
                }
            }
            list.addView(row)
        }
    }

    private companion object {
        /** The bubble's actions, in the order the strip shows them. */
        val HELP_BUTTONS = listOf(
            HelpButton(
                R.string.help_button_full_screen_title,
                R.string.help_button_full_screen_desc,
                icon = R.drawable.ic_menu_whole_screen,
            ),
            HelpButton(
                R.string.help_button_region_title,
                R.string.help_button_region_desc,
                icon = R.drawable.ic_menu_region,
            ),
            HelpButton(
                R.string.help_button_camera_title,
                R.string.help_button_camera_desc,
                icon = R.drawable.ic_menu_camera,
            ),
            HelpButton(
                R.string.help_button_kanji_title,
                R.string.help_button_kanji_desc,
                glyph = "漢",
            ),
            HelpButton(
                R.string.help_button_dictionary_title,
                R.string.help_button_dictionary_desc,
                icon = R.drawable.ic_menu_dictionary,
            ),
            HelpButton(
                R.string.help_button_settings_title,
                R.string.help_button_settings_desc,
                icon = R.drawable.ic_menu_settings,
            ),
            HelpButton(
                R.string.help_button_stop_title,
                R.string.help_button_stop_desc,
                icon = R.drawable.ic_menu_stop,
            ),
        )
    }
}
