package dev.yabai.ocr.desktop

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.yabai.ocr.core.anki.AnkiFields
import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.anki.AnkiTemplate
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Why a card could not be handed to Anki, so the panel can say what the reader has to fix. */
enum class AnkiFailure { NOT_RUNNING, ADDON_MISSING, AUTH, DUPLICATE, UNKNOWN }

class AnkiException(val failure: AnkiFailure, message: String) : Exception(message)

/**
 * The slice of AnkiConnect the app needs.
 *
 * An interface so the sender can be tested without an Anki: a collector is the user's, not the
 * test's, and the add-on may not be installed on the machine running the build.
 */
interface AnkiClient {
    fun version(): Int
    fun ensureDeck(deck: String)
    fun ensureModel(model: String, fields: List<String>)
    fun addNote(deck: String, model: String, note: AnkiNote): Long
    fun deleteNote(noteId: Long)
}

/** Which wire format the add-on answered, so the probe happens once per client and not per call. */
private enum class AnkiProtocol { LEGACY, V1 }

/**
 * AnkiConnect, the add-on that lets the desktop read and write the collection Anki already has open.
 *
 * Nothing here writes `collection.anki2`: the app only sends what Anki's own API accepts, with Anki
 * running, so a crash of this process cannot corrupt a collection. The endpoint is the add-on's
 * local one, `127.0.0.1:8765` by default.
 *
 * The legacy JSON (`POST /` with `version: 6`) is the format every AnkiConnect release answers, so it
 * is tried first. A newer add-on that only serves the v1 JSON-RPC endpoint (`POST /api/v1`) refuses
 * the legacy path with HTTP 404/405; then the same operations are spoken in v1 (`decks.*`,
 * `noteTypes.*`, `notes.*`) and the answer is remembered. A real failure — connection refused, a
 * refused key — does not fall back: it is what the panel has to report.
 */
class AnkiConnect(
    private val baseUrl: String = "http://127.0.0.1:8765",
    private val apiKey: String = "",
    private val http: OkHttpClient = defaultHttp(),
) : AnkiClient {

    @Volatile
    private var wire: AnkiProtocol? = null
    private val nextId = AtomicInteger(1)

    /** The protocol level that answered: 6 for the legacy JSON, 1 for the JSON-RPC endpoint. */
    override fun version(): Int {
        when (wire) {
            AnkiProtocol.LEGACY -> return legacy("version").asInt
            AnkiProtocol.V1 -> return v1Version()
            null -> Unit
        }
        val first = runCatching { legacy("version").asInt }
        first.getOrNull()?.let {
            wire = AnkiProtocol.LEGACY
            return it
        }
        val refusal = first.exceptionOrNull()
        // 404/405 on `POST /` is a v1-only add-on; anything else is a failure the panel must report.
        if ((refusal as? AnkiException)?.failure != AnkiFailure.ADDON_MISSING) {
            throw refusal ?: IllegalStateException("AnkiConnect did not answer the handshake")
        }
        try {
            v1Version()
        } catch (_: AnkiException) {
            // Neither format answered: the add-on really is missing, which is the useful message.
            throw refusal
        }
        wire = AnkiProtocol.V1
        return V1_MAJOR
    }

    override fun ensureDeck(deck: String) {
        if (protocol() == AnkiProtocol.V1) {
            val names = v1("decks.list").asJsonArray.mapNotNull(::name)
            if (deck in names) return
            v1("decks.create", JsonObject().apply { addProperty("name", deck) })
            return
        }
        val names = legacy("deckNames").asJsonArray
        if (names.any { it.asString == deck }) return
        legacy("createDeck", JsonObject().apply { addProperty("deck", deck) })
    }

    override fun ensureModel(model: String, fields: List<String>) {
        if (protocol() == AnkiProtocol.V1) {
            val names = v1("noteTypes.list").asJsonArray.mapNotNull(::name)
            if (model in names) return
            v1(
                "noteTypes.create",
                JsonObject().apply {
                    addProperty("name", model)
                    add("fields", JsonArray().apply {
                        fields.forEach { field -> add(JsonObject().apply { addProperty("name", field) }) }
                    })
                    add("templates", JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("name", AnkiTemplate.NAME)
                                addProperty("questionHtml", AnkiTemplate.FRONT)
                                addProperty("answerHtml", AnkiTemplate.BACK)
                            },
                        )
                    })
                    addProperty("sortField", fields.firstOrNull() ?: AnkiFields.WORD)
                },
            )
            return
        }
        val names = legacy("modelNames").asJsonArray
        if (names.any { it.asString == model }) return
        legacy(
            "createModel",
            JsonObject().apply {
                addProperty("modelName", model)
                add("inOrderFields", JsonArray().apply { fields.forEach { add(it) } })
                add(
                    "cardTemplates",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("Name", AnkiTemplate.NAME)
                                addProperty("Front", AnkiTemplate.FRONT)
                                addProperty("Back", AnkiTemplate.BACK)
                            },
                        )
                    },
                )
            },
        )
    }

    override fun addNote(deck: String, model: String, note: AnkiNote): Long {
        if (protocol() == AnkiProtocol.V1) {
            // v1 has no `canAddNotes`: the duplicate policy travels with the creation, and a refusal
            // comes back as a validation error that `classify` turns into DUPLICATE.
            val created = v1(
                "notes.create",
                JsonObject().apply {
                    add("noteType", JsonObject().apply { addProperty("name", model) })
                    add("deck", JsonObject().apply { addProperty("name", deck) })
                    add("fields", fields(note))
                    add("tags", JsonArray().apply { note.tags.forEach { add(it) } })
                    add("duplicates", JsonObject().apply { addProperty("allow", false) })
                },
            ).asJsonObject
            return created.get("noteId")?.takeIf { !it.isJsonNull }?.asString?.toLongOrNull()
                ?: throw AnkiException(AnkiFailure.UNKNOWN, "AnkiConnect answered without a note id")
        }
        val payload = JsonObject().apply {
            addProperty("deckName", deck)
            addProperty("modelName", model)
            add("fields", fields(note))
            add("tags", JsonArray().apply { note.tags.forEach { add(it) } })
            add("options", JsonObject().apply { addProperty("allowDuplicate", false) })
        }
        val verdict = legacy(
            "canAddNotesWithErrorDetail",
            JsonObject().apply { add("notes", JsonArray().apply { add(payload) }) },
        ).asJsonArray.firstOrNull()?.asJsonObject
        if (verdict?.get("canAdd")?.takeIf { !it.isJsonNull }?.asBoolean != true) {
            // `canAddNotes` answers false for a duplicate and for a card the note type refuses
            // alike; only the add-on's own words tell them apart, and only a duplicate is already
            // in Anki. Anything else stays in the queue with the reason Anki gave.
            val reason = verdict?.get("error")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            throw AnkiException(
                if (reason.isBlank()) AnkiFailure.UNKNOWN else classify(reason),
                reason.ifBlank { "Anki refused the card without saying why" },
            )
        }
        return legacy(
            "addNote",
            JsonObject().apply { add("note", payload) },
        ).asLong
    }

    override fun deleteNote(noteId: Long) {
        if (protocol() == AnkiProtocol.V1) {
            v1(
                "notes.delete",
                JsonObject().apply { add("ids", JsonArray().apply { add(noteId.toString()) }) },
            )
            return
        }
        legacy(
            "deleteNotes",
            JsonObject().apply { add("notes", JsonArray().apply { add(noteId) }) },
        )
    }

    private fun fields(note: AnkiNote): JsonObject = JsonObject().apply {
        for (name in AnkiFields.ORDER) addProperty(name, note.fields[name].orEmpty())
    }

    /** `noteTypes.list` and `decks.list` answer objects; the legacy ones answer bare names. */
    private fun name(element: JsonElement): String? =
        runCatching { element.asJsonObject.get("name").asString }.getOrNull()

    private fun protocol(): AnkiProtocol {
        wire?.let { return it }
        version()
        return wire ?: AnkiProtocol.LEGACY
    }

    private fun v1Version(): Int {
        val description = v1("api.describe").asJsonObject
        return description.get("apiMajor")?.takeIf { !it.isJsonNull }?.asInt ?: V1_MAJOR
    }

    /**
     * One legacy action, with the three failures that matter told apart: the add-on is not
     * answering, the add-on is not installed, or Anki itself rejected the request.
     */
    private fun legacy(action: String, params: JsonObject = JsonObject()): JsonElement {
        val body = JsonObject().apply {
            addProperty("action", action)
            addProperty("version", 6)
            add("params", params)
            if (apiKey.isNotBlank()) addProperty("key", apiKey)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/").post(body).build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw AnkiException(
                AnkiFailure.NOT_RUNNING,
                "AnkiConnect is not answering at $baseUrl: ${e.message ?: "connection refused"}",
            )
        }
        response.use {
            failForStatus(it.code, legacy = true)
            val text = it.body?.string().orEmpty()
            val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                ?: throw AnkiException(AnkiFailure.UNKNOWN, "AnkiConnect returned something that is not JSON")
            val error = json.get("error")?.takeIf { !it.isJsonNull }?.asString
            if (error != null) throw AnkiException(classify(error), error)
            return json.get("result") ?: JsonObject()
        }
    }

    /**
     * One v1 call. JSON-RPC answers errors with HTTP 200 and an `error` object, so the body is read
     * before deciding: a duplicate is a duplicate, not a broken connection.
     */
    private fun v1(method: String, params: JsonObject? = null): JsonElement {
        val body = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", nextId.getAndIncrement())
            addProperty("method", method)
            if (params != null && params.size() > 0) add("params", params)
        }.toString().toRequestBody(JSON)

        val builder = Request.Builder().url(baseUrl.trimEnd('/') + "/api/v1").post(body)
        // v1 takes the key in the header; the body field of the legacy format is ignored there.
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")

        val response = try {
            http.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw AnkiException(
                AnkiFailure.NOT_RUNNING,
                "AnkiConnect is not answering at $baseUrl: ${e.message ?: "connection refused"}",
            )
        }
        response.use {
            if (it.code == 403) {
                throw AnkiException(
                    AnkiFailure.AUTH,
                    "AnkiConnect rejected the request origin (HTTP 403)",
                )
            }
            failForStatus(it.code, legacy = false)
            val text = it.body?.string().orEmpty()
            val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                ?: throw AnkiException(AnkiFailure.UNKNOWN, "AnkiConnect returned something that is not JSON")
            val error = json.getAsJsonObject("error")
            if (error != null) {
                val message = error.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                throw AnkiException(
                    classify(message),
                    message.ifBlank { "AnkiConnect answered error ${error.get("code")}" },
                )
            }
            return json.get("result") ?: JsonObject()
        }
    }

    private fun failForStatus(code: Int, legacy: Boolean) {
        when {
            code == 401 -> throw AnkiException(
                AnkiFailure.AUTH,
                "AnkiConnect refused the API key (HTTP 401)",
            )
            code == 404 || code == 405 -> throw AnkiException(
                AnkiFailure.ADDON_MISSING,
                if (legacy) {
                    "No AnkiConnect at $baseUrl (HTTP $code); install add-on 2055492159"
                } else {
                    "AnkiConnect at $baseUrl has no /api/v1 (HTTP $code)"
                },
            )
            code !in 200..299 -> throw AnkiException(AnkiFailure.UNKNOWN, "AnkiConnect answered HTTP $code")
        }
    }

    private fun classify(error: String): AnkiFailure {
        val text = error.lowercase()
        return when {
            "duplicate" in text -> AnkiFailure.DUPLICATE
            "api key" in text || "authentic" in text -> AnkiFailure.AUTH
            else -> AnkiFailure.UNKNOWN
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** The v1 JSON-RPC protocol level, what `api.describe` reports as `apiMajor`. */
        const val V1_MAJOR = 1

        /**
         * A second of patience to connect and five to answer: Anki is local, and a collector busy
         * saving must not make the panel wait like a network call.
         */
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
