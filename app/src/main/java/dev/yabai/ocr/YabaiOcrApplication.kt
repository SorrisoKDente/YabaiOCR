package dev.yabai.ocr

import android.app.Application

class YabaiOcrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = AppSettings(this)
        settings.applyTheme()
    }
}
