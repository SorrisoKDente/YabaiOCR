package dev.yabai.ocr.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import dev.yabai.ocr.core.anki.AnkiFields
import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.anki.AnkiTemplate

/** Why a card could not be handed to AnkiDroid; the panel turns each into a sentence (A5). */
enum class AnkiDroidFailure {
    NOT_INSTALLED,
    PERMISSION,
    API_DISABLED,
    MISSING_FIELD,
    INSERT_FAILED,
    DELETE_FAILED,
}

class AnkiDroidException(val failure: AnkiDroidFailure, message: String) : Exception(message)

/** What happened to a card: added with [noteId], or already in the collection. */
data class AnkiDroidSendResult(val noteId: Long, val duplicate: Boolean)

/**
 * The AnkiDroid content provider, spoken directly.
 *
 * AnkiDroid is the only supported door on Android: writing its collection from outside is not
 * supported and would corrupt it. The contract is small and stable (`content://com.ichi2.anki.flashcards`,
 * fields joined by 0x1f), so this talks to it without the JitPack dependency.
 *
 * The provider does **not** refuse duplicates itself; the key (first field) is looked up before the
 * insert, the same check the desktop asks AnkiConnect to make. It runs before the deck and note
 * type are created, so a duplicate leaves nothing behind.
 */
class AnkiDroidGateway(
    context: Context,
    private val authority: String = AnkiDroidContract.AUTHORITY,
) {
    private val context: Context = context.applicationContext
    private val resolver: ContentResolver = this.context.contentResolver

    /** Whether AnkiDroid's provider is visible to this app (the manifest `<queries>` is what makes it so). */
    fun isInstalled(): Boolean =
        context.packageManager.resolveContentProvider(authority, 0) != null

    /** Whether the user granted `com.ichi2.anki.permission.READ_WRITE_DATABASE`. */
    fun hasPermission(): Boolean =
        context.checkSelfPermission(AnkiDroidContract.PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Adds [note] to [deckName], creating the deck and the `Yabai` note type when they are missing.
     *
     * A card whose first field is already in the collection is not inserted again: two taps on the
     * same word must not make two cards.
     */
    fun send(
        note: AnkiNote,
        deckName: String = DEFAULT_DECK,
        modelName: String = DEFAULT_MODEL,
    ): AnkiDroidSendResult {
        requireReady()
        // A duplicate must not leave a new deck or note type behind, so it is looked up before
        // anything is created. A missing note type has no notes to duplicate.
        val existing = findModel(modelName)
        if (existing != null && contains(existing.id, note.firstField)) {
            return AnkiDroidSendResult(noteId = -1, duplicate = true)
        }
        val deckId = ensureDeck(deckName)
        val model = existing ?: createModel(modelName, deckId)
        return AnkiDroidSendResult(insert(deckId, model, note), duplicate = false)
    }

    /** The «Desfazer» of a card that went to AnkiDroid. */
    fun deleteNote(noteId: Long) {
        requireReady()
        val deleted = provider { resolver.delete(noteUri(noteId), null, null) }
        if (deleted <= 0) {
            throw AnkiDroidException(
                AnkiDroidFailure.DELETE_FAILED,
                "AnkiDroid did not remove note $noteId",
            )
        }
    }

    private fun requireReady() {
        if (!isInstalled()) {
            throw AnkiDroidException(AnkiDroidFailure.NOT_INSTALLED, "AnkiDroid is not installed")
        }
        if (!hasPermission()) {
            throw AnkiDroidException(
                AnkiDroidFailure.PERMISSION,
                "AnkiDroid permission was not granted",
            )
        }
    }

    private fun ensureDeck(name: String): Long {
        val decks = provider {
            resolver.query(
                decksUri,
                arrayOf(AnkiDroidContract.DECK_ID, AnkiDroidContract.DECK_NAME),
                null,
                null,
                null,
            )
        } ?: throw apiDisabled("no deck list")
        decks.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(AnkiDroidContract.DECK_ID)
            val deckName = cursor.getColumnIndexOrThrow(AnkiDroidContract.DECK_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(deckName) == name) return cursor.getLong(id)
            }
        }
        val uri = provider {
            resolver.insert(
                decksUri,
                ContentValues().apply { put(AnkiDroidContract.DECK_NAME, name) },
            )
        } ?: throw apiDisabled("the deck «$name» was not created")
        return uri.lastPathSegment?.toLongOrNull()
            ?: throw AnkiDroidException(
                AnkiDroidFailure.INSERT_FAILED,
                "AnkiDroid returned no id for the deck «$name»",
            )
    }

    /** The `Yabai` note type already in the collection, or null when it still has to be created. */
    private fun findModel(name: String): AnkiDroidModel? {
        val models = provider {
            resolver.query(
                modelsUri,
                arrayOf(
                    AnkiDroidContract.MODEL_ID,
                    AnkiDroidContract.MODEL_NAME,
                    AnkiDroidContract.MODEL_FIELD_NAMES,
                ),
                null,
                null,
                null,
            )
        } ?: throw apiDisabled("no note type list")
        models.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(AnkiDroidContract.MODEL_ID)
            val modelName = cursor.getColumnIndexOrThrow(AnkiDroidContract.MODEL_NAME)
            val fieldNames = cursor.getColumnIndexOrThrow(AnkiDroidContract.MODEL_FIELD_NAMES)
            while (cursor.moveToNext()) {
                if (cursor.getString(modelName) != name) continue
                val fields = cursor.getString(fieldNames).orEmpty()
                    .split(AnkiDroidContract.FIELD_SEPARATOR)
                requireFields(name, fields)
                return AnkiDroidModel(cursor.getLong(id), fields)
            }
        }
        return null
    }

    /** The note type is addressed by field **name**; the insert is positional, so a missing field is a refusal. */
    private fun requireFields(modelName: String, fields: List<String>) {
        val missing = AnkiFields.ORDER.filterNot { it in fields }
        if (missing.isNotEmpty()) {
            throw AnkiDroidException(
                AnkiDroidFailure.MISSING_FIELD,
                "The note type «$modelName» has no field ${missing.joinToString(", ")}",
            )
        }
    }

    private fun createModel(name: String, deckId: Long): AnkiDroidModel {
        val uri = provider {
            resolver.insert(
                modelsUri,
                ContentValues().apply {
                    put(AnkiDroidContract.MODEL_NAME, name)
                    put(
                        AnkiDroidContract.MODEL_FIELD_NAMES,
                        AnkiFields.ORDER.joinToString(AnkiDroidContract.FIELD_SEPARATOR),
                    )
                    put(AnkiDroidContract.MODEL_NUM_CARDS, 1)
                    put(AnkiDroidContract.MODEL_CSS, CSS)
                    put(AnkiDroidContract.DECK_ID, deckId)
                    put(AnkiDroidContract.MODEL_SORT_FIELD_INDEX, 0)
                },
            )
        } ?: throw apiDisabled("the note type «$name» was not created")
        val template = Uri.withAppendedPath(Uri.withAppendedPath(uri, "templates"), "0")
        val updated = provider {
            resolver.update(
                template,
                ContentValues().apply {
                    put(AnkiDroidContract.TEMPLATE_NAME, AnkiTemplate.NAME)
                    put(AnkiDroidContract.TEMPLATE_QUESTION, AnkiTemplate.FRONT)
                    put(AnkiDroidContract.TEMPLATE_ANSWER, AnkiTemplate.BACK)
                },
                null,
                null,
            )
        }
        if (updated <= 0) {
            throw AnkiDroidException(
                AnkiDroidFailure.INSERT_FAILED,
                "AnkiDroid did not accept the template of «$name»",
            )
        }
        val id = uri.lastPathSegment?.toLongOrNull()
            ?: throw AnkiDroidException(
                AnkiDroidFailure.INSERT_FAILED,
                "AnkiDroid returned no id for the note type «$name»",
            )
        return AnkiDroidModel(id, AnkiFields.ORDER)
    }

    private fun contains(modelId: Long, key: String): Boolean {
        val notes = provider {
            resolver.query(
                notesV2Uri,
                arrayOf(AnkiDroidContract.NOTE_FLDS),
                "${AnkiDroidContract.NOTE_MID}=?",
                arrayOf(modelId.toString()),
                null,
            )
        } ?: throw apiDisabled("no note list")
        notes.use { cursor ->
            val fields = cursor.getColumnIndexOrThrow(AnkiDroidContract.NOTE_FLDS)
            while (cursor.moveToNext()) {
                val first = cursor.getString(fields).orEmpty()
                    .substringBefore(AnkiDroidContract.FIELD_SEPARATOR)
                if (first == key) return true
            }
        }
        return false
    }

    private fun insert(deckId: Long, model: AnkiDroidModel, note: AnkiNote): Long {
        val values = ContentValues().apply {
            put(AnkiDroidContract.NOTE_MID, model.id)
            put(
                AnkiDroidContract.NOTE_FLDS,
                model.fields.joinToString(AnkiDroidContract.FIELD_SEPARATOR) { note.field(it) },
            )
            if (note.tags.isNotEmpty()) {
                put(AnkiDroidContract.NOTE_TAGS, note.tags.joinToString(" "))
            }
        }
        val uri = provider { resolver.insert(notesUri, values) }
            ?: throw apiDisabled("the note was not accepted")
        val noteId = uri.lastPathSegment?.toLongOrNull()
            ?: throw AnkiDroidException(
                AnkiDroidFailure.INSERT_FAILED,
                "AnkiDroid returned no id for the note",
            )
        try {
            moveCards(uri, deckId)
        } catch (failure: AnkiDroidException) {
            // Do not leave the note behind in the note type's default deck when the move fails.
            runCatching { provider { resolver.delete(uri, null, null) } }
            throw failure
        }
        return noteId
    }

    /**
     * The insert itself puts the cards in the note type's default deck; the destination deck is set
     * on each card afterwards, which is how AnkiDroid's own `AddContentApi` does it. A failed move is
     * propagated so [insert] can remove the note instead of leaving it in the wrong deck.
     */
    private fun moveCards(noteUri: Uri, deckId: Long) {
        val cardsUri = Uri.withAppendedPath(noteUri, "cards")
        val cards = provider {
            resolver.query(cardsUri, arrayOf(AnkiDroidContract.CARD_ORD), null, null, null)
        } ?: throw apiDisabled("no cards for the new note")
        cards.use { cursor ->
            val ord = cursor.getColumnIndexOrThrow(AnkiDroidContract.CARD_ORD)
            while (cursor.moveToNext()) {
                val cardOrd = cursor.getString(ord).orEmpty()
                val card = Uri.withAppendedPath(cardsUri, cardOrd)
                val updated = provider {
                    resolver.update(
                        card,
                        ContentValues().apply { put(AnkiDroidContract.DECK_ID, deckId) },
                        null,
                        null,
                    )
                }
                if (updated <= 0) {
                    throw AnkiDroidException(
                        AnkiDroidFailure.INSERT_FAILED,
                        "AnkiDroid did not move card $cardOrd to deck $deckId",
                    )
                }
            }
        }
    }

    /** Turns a provider that is not answering into the failure the panel has to explain. */
    private inline fun <T> provider(block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        throw AnkiDroidException(
            AnkiDroidFailure.PERMISSION,
            e.message ?: "AnkiDroid refused the permission",
        )
    } catch (e: IllegalStateException) {
        throw apiDisabled(e.message ?: "AnkiDroid's storage is not ready")
    } catch (e: IllegalArgumentException) {
        throw apiDisabled(e.message ?: "AnkiDroid refused the request")
    }

    private fun apiDisabled(what: String) = AnkiDroidException(
        AnkiDroidFailure.API_DISABLED,
        "AnkiDroid answered $what; turn on «Settings → Advanced → AnkiDroid API»",
    )

    private val decksUri: Uri get() = Uri.parse("content://$authority/decks")
    private val modelsUri: Uri get() = Uri.parse("content://$authority/models")
    private val notesUri: Uri get() = Uri.parse("content://$authority/notes")
    private val notesV2Uri: Uri get() = Uri.parse("content://$authority/notes_v2")
    private fun noteUri(id: Long): Uri = Uri.withAppendedPath(notesUri, id.toString())

    companion object {
        const val DEFAULT_DECK = "Yabai"
        const val DEFAULT_MODEL = "Yabai"

        /** What an Activity requests before the first card (A5); the manifest declares it. */
        const val PERMISSION = AnkiDroidContract.PERMISSION

        private const val CSS = ".card { font-family: arial; font-size: 20px; text-align: left; }"
    }
}

/** The AnkiDroid note type as its provider exposes it: the id and the field order. */
private data class AnkiDroidModel(val id: Long, val fields: List<String>)

/**
 * The slice of AnkiDroid's `FlashCardsContract` this app uses.
 *
 * Copied here on purpose: the contract is small and stable, and a JitPack dependency for a handful
 * of URI and column names would not pay for itself. The values are the ones the provider publishes
 * (and that AnkiDroid's own `AddContentApi` uses).
 */
object AnkiDroidContract {
    const val AUTHORITY = "com.ichi2.anki.flashcards"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    const val FIELD_SEPARATOR = "\u001f"

    const val DECK_ID = "deck_id"
    const val DECK_NAME = "deck_name"

    const val MODEL_ID = "_id"
    const val MODEL_NAME = "name"
    const val MODEL_FIELD_NAMES = "field_names"
    const val MODEL_NUM_CARDS = "num_cards"
    const val MODEL_CSS = "css"
    const val MODEL_SORT_FIELD_INDEX = "sort_field_index"

    const val NOTE_MID = "mid"
    const val NOTE_FLDS = "flds"
    const val NOTE_TAGS = "tags"

    const val CARD_ORD = "ord"

    const val TEMPLATE_NAME = "card_template_name"
    const val TEMPLATE_QUESTION = "question_format"
    const val TEMPLATE_ANSWER = "answer_format"
}
