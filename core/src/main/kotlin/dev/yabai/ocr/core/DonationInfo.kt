package dev.yabai.ocr.core

/**
 * Where people can support the project.
 *
 * Kept in `:core` so the desktop app and the Android app point at the same places — a wrong Pix key
 * or a dead Ko-fi link silently loses donations, and nobody notices.
 */
object DonationInfo {
    /** Pix key (Brazil), also printed as text next to the QR code in the README. */
    const val PIX_KEY: String = "yabaihonyaku@gmail.com"

    /** Ko-fi page. */
    const val KOFI_URL: String = "https://ko-fi.com/yabaihonyaku"
}
