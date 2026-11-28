package dev.yabai.ocr.anki

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import dev.yabai.ocr.core.anki.AnkiFields
import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.anki.AnkiTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config
import java.io.File

/**
 * The AnkiDroid door, tested against a provider that speaks the same contract.
 *
 * No AnkiDroid and no collection are touched: the fake provider is registered at the real authority,
 * so the gateway's URIs, columns, 0x1f separator, deck/type creation and duplicate lookup are
 * exercised exactly as they would be on the phone. The permission is the manifest's AnkiDroid
 * permission, granted and denied here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class AnkiDroidGatewayTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val context: Context get() = app
    private val gateway get() = AnkiDroidGateway(context)

    /** Registers the fake provider at the real authority and grants the AnkiDroid permission. */
    private fun install(): FakeAnkiDroidProvider {
        val provider = FakeAnkiDroidProvider()
        val info = ProviderInfo().apply {
            authority = AnkiDroidContract.AUTHORITY
            // The provider is ours in the test; only the authority is the one AnkiDroid publishes.
            packageName = context.packageName
            name = "dev.yabai.ocr.anki.FakeAnkiDroidProvider"
            exported = true
        }
        ContentProviderController.of(provider).create(info)
        shadowOf(app.packageManager).addOrUpdateProvider(info)
        shadowOf(app).grantPermissions(AnkiDroidContract.PERMISSION)
        return provider
    }

    private fun note(word: String = "食べる") = AnkiNote(
        linkedMapOf(
            AnkiFields.WORD to word,
            AnkiFields.READING to "たべる",
            AnkiFields.MEANING to "comer",
            AnkiFields.SENTENCE to "食べました",
            AnkiFields.SOURCE to "Jitendex · 27/11/2026",
        ),
        tags = listOf("yabai"),
    )

    /** Without these two entries the provider is invisible on Android 11+ and the API has no access. */
    @Test
    fun `the manifest declares AnkiDroid's permission and its package query`() {
        val manifest = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).firstOrNull { it.isFile }?.readText().orEmpty()

        assertTrue("the manifest is not readable", manifest.isNotEmpty())
        assertTrue(
            "AnkiDroid's read/write permission is not declared",
            manifest.contains("com.ichi2.anki.permission.READ_WRITE_DATABASE"),
        )
        assertTrue(
            "the <queries> entry that makes AnkiDroid's provider visible is missing",
            manifest.contains("<package android:name=\"com.ichi2.anki\""),
        )
    }

    @Test
    fun `without AnkiDroid the send says so`() {
        val failure = assertThrows(AnkiDroidException::class.java) { gateway.send(note()) }

        assertEquals(AnkiDroidFailure.NOT_INSTALLED, failure.failure)
    }

    @Test
    fun `without the permission nothing is read or written`() {
        val provider = install()
        shadowOf(app).denyPermissions(AnkiDroidContract.PERMISSION)

        val failure = assertThrows(AnkiDroidException::class.java) { gateway.send(note()) }

        assertEquals(AnkiDroidFailure.PERMISSION, failure.failure)
        assertEquals("a deck was created without the permission", 0, provider.deckInserts)
        assertEquals("a note was written without the permission", 0, provider.noteInserts)
    }

    @Test
    fun `an AnkiDroid with the API switched off says so`() {
        val provider = install()
        provider.disabled = true

        val failure = assertThrows(AnkiDroidException::class.java) { gateway.send(note()) }

        assertEquals(AnkiDroidFailure.API_DISABLED, failure.failure)
    }

    @Test
    fun `a deck with the setting's name is reused instead of created again`() {
        val provider = install()
        provider.decks[7] = "Yabai"

        gateway.send(note())

        assertEquals("the existing deck was created again", 0, provider.deckInserts)
        assertEquals("the note type did not default to the chosen deck", 7L, provider.models.values.single().deckId)
        assertEquals("the card was not moved to the chosen deck", listOf(7L), provider.cardDeckUpdates)
    }

    @Test
    fun `the Yabai note type is created with the five fields and one template`() {
        val provider = install()

        gateway.send(note())

        val model = provider.models.values.single()
        assertEquals(AnkiFields.ORDER, model.fields)
        assertEquals(1, model.numCards)
        assertEquals(0, model.sortFieldIndex)
        assertEquals(provider.decks.keys.single(), model.deckId)

        val template = provider.templates.single()
        assertEquals(AnkiTemplate.NAME, template.getAsString(AnkiDroidContract.TEMPLATE_NAME))
        assertEquals(AnkiTemplate.FRONT, template.getAsString(AnkiDroidContract.TEMPLATE_QUESTION))
        assertEquals(AnkiTemplate.BACK, template.getAsString(AnkiDroidContract.TEMPLATE_ANSWER))
    }

    @Test
    fun `a note type missing a Yabai field is refused instead of half filled`() {
        val provider = install()
        provider.models[42] = FakeModel(
            id = 42,
            name = "Yabai",
            fields = listOf(AnkiFields.WORD, AnkiFields.READING),
            deckId = 1,
        )

        val failure = assertThrows(AnkiDroidException::class.java) { gateway.send(note()) }

        assertEquals(AnkiDroidFailure.MISSING_FIELD, failure.failure)
        assertEquals("a note was written into a note type it does not fit", 0, provider.noteInserts)
        assertEquals("a deck was created for a card that cannot fit", 0, provider.deckInserts)
    }

    @Test
    fun `the fields are joined in the order the note type declares`() {
        val provider = install()
        val reversed = AnkiFields.ORDER.reversed()
        provider.decks[1] = "Yabai"
        provider.models[42] = FakeModel(42, "Yabai", reversed, deckId = 1)

        val card = note()
        gateway.send(card)

        val expected = reversed.map { card.field(it) }
        val written = provider.notes.values.single()
        assertEquals(expected, written.flds.split(AnkiDroidContract.FIELD_SEPARATOR))
        assertEquals(listOf("yabai"), written.tags.split(" "))
    }

    @Test
    fun `a word already in the collection is reported and not added twice`() {
        val provider = install()

        val first = gateway.send(note())
        val second = gateway.send(note())

        assertFalse("the first send was called a duplicate", first.duplicate)
        assertTrue("the same word was added twice", second.duplicate)
        assertEquals("a duplicate came back with a note id", -1L, second.noteId)
        assertEquals(1, provider.notes.size)
        assertEquals(1, provider.noteInserts)
    }

    @Test
    fun `a duplicate does not create the deck or the note type`() {
        val provider = install()
        val card = note()
        provider.models[42] = FakeModel(42, "Yabai", AnkiFields.ORDER, deckId = 1)
        provider.notes[102] = FakeNote(
            102,
            42,
            card.fields.values.joinToString(AnkiDroidContract.FIELD_SEPARATOR),
            card.tags.joinToString(" "),
        )

        val result = gateway.send(card)

        assertTrue("the existing note was not reported as a duplicate", result.duplicate)
        assertEquals("a duplicate created a deck", 0, provider.deckInserts)
        assertEquals("a duplicate created a note type", setOf(42L), provider.models.keys)
        assertEquals("a duplicate wrote a note", 0, provider.noteInserts)
    }

    @Test
    fun `a note whose card cannot move is removed instead of left in the wrong deck`() {
        val provider = install()
        provider.refuseMoves = true

        val failure = assertThrows(AnkiDroidException::class.java) { gateway.send(note()) }

        assertEquals(AnkiDroidFailure.INSERT_FAILED, failure.failure)
        assertTrue("the note was left behind in the wrong deck", provider.notes.isEmpty())
    }

    @Test
    fun `undo removes the note the card created`() {
        val provider = install()
        val sent = gateway.send(note())

        gateway.deleteNote(sent.noteId)

        assertTrue("the note is still in the collection", provider.notes.isEmpty())
        assertEquals(listOf(sent.noteId), provider.deleted)
    }
}

/**
 * An AnkiDroid provider with just the tables the gateway uses.
 *
 * It records what was written, so the tests can hold the column names, the field order and the
 * 0x1f separator rather than the gateway's own idea of them.
 */
private data class FakeModel(
    val id: Long,
    val name: String,
    val fields: List<String>,
    val deckId: Long,
    val numCards: Int = 1,
    val sortFieldIndex: Int = 0,
)

private data class FakeNote(val id: Long, val modelId: Long, val flds: String, val tags: String)

private class FakeAnkiDroidProvider : ContentProvider() {

    /** An AnkiDroid whose «Advanced → AnkiDroid API» is off answers nothing. */
    var disabled = false

    /** Makes the card move fail, to see whether the note is rolled back. */
    var refuseMoves = false

    val decks = linkedMapOf<Long, String>()
    val models = linkedMapOf<Long, FakeModel>()
    val notes = linkedMapOf<Long, FakeNote>()
    val templates = mutableListOf<ContentValues>()
    val cardDeckUpdates = mutableListOf<Long>()
    val deleted = mutableListOf<Long>()
    var deckInserts = 0
    var noteInserts = 0
    private var nextId = 100L

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (disabled) return null
        return when (uri.pathSegments.firstOrNull()) {
            "decks" -> MatrixCursor(
                arrayOf(AnkiDroidContract.DECK_ID, AnkiDroidContract.DECK_NAME),
            ).apply {
                decks.forEach { (id, name) -> addRow(arrayOf<Any>(id, name)) }
            }

            "models" -> MatrixCursor(
                arrayOf(
                    AnkiDroidContract.MODEL_ID,
                    AnkiDroidContract.MODEL_NAME,
                    AnkiDroidContract.MODEL_FIELD_NAMES,
                ),
            ).apply {
                models.values.forEach { model ->
                    addRow(
                        arrayOf<Any>(
                            model.id,
                            model.name,
                            model.fields.joinToString(AnkiDroidContract.FIELD_SEPARATOR),
                        ),
                    )
                }
            }

            "notes_v2" -> {
                val mid = if (selection?.contains(AnkiDroidContract.NOTE_MID) == true) {
                    selectionArgs?.firstOrNull()?.toLongOrNull()
                } else {
                    null
                }
                MatrixCursor(arrayOf(AnkiDroidContract.NOTE_FLDS)).apply {
                    notes.values.filter { mid == null || it.modelId == mid }.forEach { note ->
                        addRow(arrayOf<Any>(note.flds))
                    }
                }
            }

            "notes" -> if (uri.pathSegments.getOrNull(2) == "cards") {
                MatrixCursor(arrayOf(AnkiDroidContract.CARD_ORD)).apply { addRow(arrayOf<Any>(0)) }
            } else {
                null
            }

            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (disabled || values == null) return null
        val id = nextId++
        return when (uri.pathSegments.firstOrNull()) {
            "decks" -> {
                deckInserts++
                decks[id] = values.getAsString(AnkiDroidContract.DECK_NAME).orEmpty()
                Uri.withAppendedPath(uri, id.toString())
            }

            "models" -> {
                models[id] = FakeModel(
                    id = id,
                    name = values.getAsString(AnkiDroidContract.MODEL_NAME).orEmpty(),
                    fields = values.getAsString(AnkiDroidContract.MODEL_FIELD_NAMES).orEmpty()
                        .split(AnkiDroidContract.FIELD_SEPARATOR),
                    deckId = values.getAsLong(AnkiDroidContract.DECK_ID) ?: 0,
                    numCards = values.getAsInteger(AnkiDroidContract.MODEL_NUM_CARDS) ?: 0,
                    sortFieldIndex = values.getAsInteger(
                        AnkiDroidContract.MODEL_SORT_FIELD_INDEX,
                    ) ?: 0,
                )
                Uri.withAppendedPath(uri, id.toString())
            }

            "notes" -> {
                noteInserts++
                notes[id] = FakeNote(
                    id = id,
                    modelId = values.getAsLong(AnkiDroidContract.NOTE_MID) ?: 0,
                    flds = values.getAsString(AnkiDroidContract.NOTE_FLDS).orEmpty(),
                    tags = values.getAsString(AnkiDroidContract.NOTE_TAGS).orEmpty(),
                )
                Uri.withAppendedPath(uri, id.toString())
            }

            else -> null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        if (disabled || values == null) return 0
        val segments = uri.pathSegments
        return when {
            segments.firstOrNull() == "models" && segments.getOrNull(2) == "templates" -> {
                templates += ContentValues(values)
                1
            }

            segments.firstOrNull() == "notes" && segments.getOrNull(2) == "cards" -> {
                if (refuseMoves) {
                    0
                } else {
                    cardDeckUpdates += values.getAsLong(AnkiDroidContract.DECK_ID) ?: 0
                    1
                }
            }

            else -> 0
        }
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        if (disabled) return 0
        if (uri.pathSegments.firstOrNull() != "notes") return 0
        val id = uri.pathSegments.getOrNull(1)?.toLongOrNull() ?: return 0
        if (notes.remove(id) == null) return 0
        deleted += id
        return 1
    }
}
