package dev.yabai.ocr.core.anki

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.UUID

/** Where a card of the queue stands. */
enum class AnkiQueueState { PENDING, FAILED }

/**
 * A card waiting to be handed to Anki.
 *
 * The deck and the note type are stored with it: a retry must use the destination the card was made
 * for, not whatever the settings say later.
 */
data class AnkiQueuedNote(
    val id: String,
    val createdAt: Long,
    val deck: String,
    val model: String,
    val note: AnkiNote,
    val state: AnkiQueueState = AnkiQueueState.PENDING,
    val error: String = "",
) {
    /** Deck + note type + first field: what makes two taps on the same word one card. */
    val key: String get() = keyOf(deck, model, note)

    companion object {
        fun keyOf(deck: String, model: String, note: AnkiNote): String =
            "$deck\u0000$model\u0000${note.firstField}"
    }
}

/**
 * The queue that keeps a card from being lost when the Anki is closed, the add-on is missing or the
 * AnkiDroid is not installed.
 *
 * The file is JSON Lines, one card per line, so a line that cannot be read costs that line and not
 * the whole queue. The clock and the file are injected: the test never touches the user's queue.
 *
 * A card that was sent is removed; a card that failed stays with the reason, and the next retry
 * sends it. The queue is capped so a collection the user never opens cannot fill the disk.
 */
class AnkiQueue(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 500,
) {

    /** Everything still to be sent, oldest first. */
    fun pending(): List<AnkiQueuedNote> {
        if (!file.isFile) return emptyList()
        return runCatching { file.readLines() }
            .getOrDefault(emptyList())
            .mapNotNull(::parse)
            .sortedBy { it.createdAt }
    }

    /**
     * Adds [note] unless a card with the same key is already queued.
     *
     * The existing card is returned in that case — a second tap on the same word is not a second
     * card, on either platform.
     */
    fun enqueue(deck: String, model: String, note: AnkiNote): AnkiQueuedNote {
        val all = pending()
        val key = AnkiQueuedNote.keyOf(deck, model, note)
        all.firstOrNull { it.key == key }?.let { return it }

        val added = AnkiQueuedNote(
            id = UUID.randomUUID().toString(),
            createdAt = clock(),
            deck = deck,
            model = model,
            note = note,
        )
        val capped = (all + added).sortedBy { it.createdAt }
            .takeLast(maxEntries.coerceAtLeast(1))
        write(capped)
        return added
    }

    /** The card reached Anki: it leaves the queue. */
    fun remove(id: String) {
        val all = pending()
        write(all.filterNot { it.id == id })
    }

    /** The card could not be sent now; it stays, with the reason the panel will print. */
    fun markFailed(id: String, reason: String) {
        val all = pending()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return
        write(
            all.toMutableList().also {
                it[index] = it[index].copy(state = AnkiQueueState.FAILED, error = reason)
            },
        )
    }

    private fun write(entries: List<AnkiQueuedNote>) {
        if (entries.isEmpty()) {
            runCatching { file.delete() }
            return
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(entries.joinToString("\n", postfix = "\n") { line(it) })
        }
    }

    private fun line(entry: AnkiQueuedNote): String = JsonObject().apply {
        addProperty("id", entry.id)
        addProperty("createdAt", entry.createdAt)
        addProperty("deck", entry.deck)
        addProperty("model", entry.model)
        addProperty("state", entry.state.name)
        addProperty("error", entry.error)
        add("fields", JsonObject().apply {
            for (name in AnkiFields.ORDER) {
                addProperty(name, entry.note.fields[name].orEmpty())
            }
        })
        add("tags", JsonArray().apply { entry.note.tags.forEach { add(it) } })
    }.toString()

    private fun parse(line: String): AnkiQueuedNote? = runCatching {
        val json = JsonParser.parseString(line).asJsonObject
        val fields = LinkedHashMap<String, String>()
        json.getAsJsonObject("fields")?.entrySet()?.forEach { (name, value) ->
            fields[name] = value.asString
        }
        val tags = json.getAsJsonArray("tags")?.map { it.asString }.orEmpty()
        AnkiQueuedNote(
            id = json.get("id").asString,
            createdAt = json.get("createdAt").asLong,
            deck = json.get("deck").asString,
            model = json.get("model").asString,
            note = AnkiNote(fields, tags),
            state = runCatching { AnkiQueueState.valueOf(json.get("state").asString) }
                .getOrDefault(AnkiQueueState.PENDING),
            error = json.get("error")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
        )
    }.getOrNull()
}
