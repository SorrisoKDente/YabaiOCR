package dev.yabai.ocr.dict

import android.content.Context
import android.util.Log
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.R
import dev.yabai.ocr.overlay.ResultOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DictionarySearch"

/**
 * The answer to a query typed in the search box (R31, R35e, R37).
 *
 * It is deliberately **not** the capture path: no translation, no furigana over a sentence and no
 * «Consultando…» between keystrokes. The whole word is tried first (see [LookupEngine.search]), and
 * the suggestions are the query's neighbours in the same dictionaries the answer came from.
 *
 * The service that owns the floating panel, the activity that opens when there is no overlay
 * permission and the panel that another app asks for (ReadEra) all answer with this: one query, one
 * meaning of «the dictionary», wherever the box was drawn.
 */
suspend fun searchWords(context: Context, query: String): ResultOverlay.WordsState = try {
    val settings = AppSettings(context)
    val repository = DictionaryRepository.get(context)
    withContext(Dispatchers.Default) {
        val engine = repository.lookupEngine()
        val titles = repository.enabledTitles()
        val stale = repository.installed()
            .filter { it.enabled && it.info.outdated }
            .map { it.displayName }
        // Same rule as a capture: a filter nobody answers to is a leftover, not a choice.
        val filter = repository.resolveFilter(settings.dictionaryFilter)
        if (filter == null && settings.dictionaryFilter.isNotBlank()) {
            settings.dictionaryFilter = ""
        }
        val only = filter?.let { setOf(it) }.orEmpty()
        val text = settings.dictionaryQuery(query.trim())
        val byTranslation = settings.searchByTranslation
        if (engine == null || text.isEmpty()) {
            ResultOverlay.WordsState.Done(
                matches = emptyList(),
                kanji = emptyList(),
                dictionaryCount = titles.size,
                titles = titles,
                selected = filter,
                stale = stale,
                query = query,
                searchByTranslation = byTranslation,
            )
        } else if (byTranslation) {
            // A meaning query is Latin text and cannot be a kanji or a reading, so the kanji block
            // and the headword suggestions would be noise (R35f).
            ResultOverlay.WordsState.Done(
                matches = engine.searchTranslation(text, only = only),
                kanji = emptyList(),
                dictionaryCount = titles.size,
                titles = titles,
                selected = filter,
                stale = stale,
                query = query,
                searchByTranslation = true,
            )
        } else {
            ResultOverlay.WordsState.Done(
                matches = engine.search(text, only = only),
                kanji = engine.kanjiBreakdown(text, only = only),
                dictionaryCount = titles.size,
                titles = titles,
                selected = filter,
                stale = stale,
                query = query,
                suggestions = engine.suggest(text, only = only),
            )
        }
    }
} catch (t: Throwable) {
    Log.e(TAG, "dictionary search failed", t)
    ResultOverlay.WordsState.Failed(t.message ?: context.getString(R.string.dictionary_failed))
}
