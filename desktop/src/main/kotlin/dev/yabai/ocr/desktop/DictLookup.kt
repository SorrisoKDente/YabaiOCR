package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.desktop.i18n.DesktopStrings

/** What one dictionary query left for the window to show. */
data class DictLookupState(
    val matches: List<TokenMatch> = emptyList(),
    val error: String? = null,
)

/**
 * Runs one dictionary query without letting a broken dictionary reach the window's coroutine.
 *
 * A compiled `.yabai` is a memory-mapped folder: it fails to answer while the dictionary manager is
 * replacing it, while the user moves the folder, or when one of the parts cannot be read. Thrown
 * from a `LaunchedEffect`, that exception leaves Compose as «Fatal exception in coroutines
 * machinery» in a system dialog — the popup keeps its state but stops updating, and the message says
 * nothing about the dictionary. The query now returns the failure, and the window prints it where
 * the entries would go.
 */
object DictLookup {
    fun run(
        text: String,
        only: Set<String>,
        query: (String, Set<String>) -> List<TokenMatch>,
    ): DictLookupState = try {
        DictLookupState(matches = query(text, only))
    } catch (e: Exception) {
        DictLookupState(error = e.message ?: DesktopStrings.lookupFailedGeneric)
    }
}
