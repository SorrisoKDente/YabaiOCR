package dev.yabai.ocr.desktop

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import dev.yabai.ocr.core.anki.AnkiFields
import dev.yabai.ocr.core.anki.AnkiNote
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The add-on is spoken to the way it documents: one JSON action per request, `version: 6`, and the
 * error string read instead of being swallowed.
 *
 * The server is local and scripted, so the test needs no Anki; the sequence is asserted because the
 * order is what makes a note type exist before a card tries to use it.
 */
class AnkiConnectTest {

    private var server: HttpServer? = null
    private val actions = mutableListOf<String>()
    private val bodies = mutableListOf<JsonObject>()
    private val paths = mutableListOf<String>()
    private val requests = mutableListOf<JsonObject>()
    private val authorizations = mutableListOf<String?>()

    private fun start(respond: (String, JsonObject) -> String): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val json = JsonParser.parseString(body).asJsonObject
            val action = json.get("action").asString
            actions += action
            bodies += json
            val answer = respond(action, json)
            val bytes = answer.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}"
    }

    /**
     * A local add-on that answers only the newer JSON-RPC endpoint, as a v1-only release would: the
     * legacy `POST /` is a 404, so the client has to fall back and then remember the choice.
     */
    private fun startV1(respond: (String, JsonObject) -> String): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/api/v1") { exchange ->
            val json = JsonParser.parseString(
                exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8),
            ).asJsonObject
            paths += exchange.requestURI.path
            requests += json
            authorizations += exchange.requestHeaders.getFirst("Authorization")
            val bytes = respond(json.get("method").asString, json).toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.createContext("/") { exchange ->
            paths += exchange.requestURI.path
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}"
    }

    private fun envelope(json: JsonObject, result: String): String =
        """{"jsonrpc":"2.0","id":${json.get("id").asInt},"result":$result}"""

    @After
    fun stop() {
        server?.stop(0)
    }

    private fun note() = AnkiNote(
        linkedMapOf(
            AnkiFields.WORD to "食<ruby>べ<rt>た</rt></ruby>",
            AnkiFields.READING to "たべる",
            AnkiFields.MEANING to "comer",
            AnkiFields.SENTENCE to "食べました",
            AnkiFields.SOURCE to "Jitendex · 27/11/2026",
        ),
        listOf("yabai"),
    )

    @Test
    fun `a first card creates the deck and the note type before adding`() {
        val url = start { action, _ ->
            when (action) {
                "version" -> """{"result":6,"error":null}"""
                "deckNames" -> """{"result":[],"error":null}"""
                "modelNames" -> """{"result":[],"error":null}"""
                "canAddNotesWithErrorDetail" -> """{"result":[{"canAdd":true,"error":null}],"error":null}"""
                "addNote" -> """{"result":1234,"error":null}"""
                else -> """{"result":null,"error":null}"""
            }
        }

        val client = AnkiConnect(url)
        assertEquals(6, client.version())
        client.ensureDeck("Yabai")
        client.ensureModel("Yabai", AnkiFields.ORDER)
        assertEquals(1234L, client.addNote("Yabai", "Yabai", note()))

        assertEquals(
            listOf(
                "version",
                "deckNames",
                "createDeck",
                "modelNames",
                "createModel",
                "canAddNotesWithErrorDetail",
                "addNote",
            ),
            actions,
        )

        val model = bodies.first { it.get("action").asString == "createModel" }
        assertEquals(
            AnkiFields.ORDER,
            model.getAsJsonObject("params").getAsJsonArray("inOrderFields").map { it.asString },
        )
        val template = model.getAsJsonObject("params").getAsJsonArray("cardTemplates")[0].asJsonObject
        assertEquals("{{Palavra}}", template.get("Front").asString)
        assertTrue("the back lost the answer separator", template.get("Back").asString.contains("<hr id=answer>"))

        val add = bodies.first { it.get("action").asString == "addNote" }.getAsJsonObject("params")
        assertEquals("Yabai", add.get("note").asJsonObject.get("deckName").asString)
        assertEquals(
            "食<ruby>べ<rt>た</rt></ruby>",
            add.get("note").asJsonObject.getAsJsonObject("fields").get("Palavra").asString,
        )
        assertTrue(
            "the card would be allowed to duplicate",
            !add.get("note").asJsonObject.getAsJsonObject("options").get("allowDuplicate").asBoolean,
        )
    }

    @Test
    fun `a duplicate the add-on names is reported as a duplicate`() {
        val url = start { action, _ ->
            when (action) {
                "version" -> """{"result":6,"error":null}"""
                "canAddNotesWithErrorDetail" ->
                    """{"result":[{"canAdd":false,"error":"cannot create note because it is a duplicate"}],"error":null}"""
                else -> """{"result":[],"error":null}"""
            }
        }

        val failure = runCatching { AnkiConnect(url).addNote("Yabai", "Yabai", note()) }
            .exceptionOrNull() as AnkiException

        assertEquals(AnkiFailure.DUPLICATE, failure.failure)
        assertTrue("the client tried to add a card it knew Anki would refuse", "addNote" !in actions)
    }

    @Test
    fun `a card the note type refuses is a refusal, not a duplicate`() {
        val url = start { action, _ ->
            when (action) {
                "version" -> """{"result":6,"error":null}"""
                "canAddNotesWithErrorDetail" ->
                    """{"result":[{"canAdd":false,"error":"cannot create note because 'Palavra' is empty"}],"error":null}"""
                else -> """{"result":[],"error":null}"""
            }
        }

        val failure = runCatching { AnkiConnect(url).addNote("Yabai", "Yabai", note()) }
            .exceptionOrNull() as AnkiException

        assertEquals(AnkiFailure.UNKNOWN, failure.failure)
        assertTrue("the reason Anki gave was lost: ${failure.message}", failure.message!!.contains("Palavra"))
        assertTrue("the client tried to add a card Anki would refuse", "addNote" !in actions)
    }

    @Test
    fun `an add-on answering with an error keeps the reason`() {
        val url = start { action, _ ->
            if (action == "version") {
                """{"result":null,"error":"API key was not provided"}"""
            } else {
                """{"result":null,"error":null}"""
            }
        }

        val failure = runCatching { AnkiConnect(url).version() }.exceptionOrNull() as AnkiException

        assertEquals(AnkiFailure.AUTH, failure.failure)
        assertTrue(failure.message!!.contains("API key"))
    }

    @Test
    fun `a v1 only add-on is spoken to in JSON-RPC and the choice is remembered`() {
        val url = startV1 { method, json ->
            envelope(
                json,
                when (method) {
                    "api.describe" ->
                        """{"apiMajor":1,"ankiVersion":"25.02","schema":{},"methods":[],"limits":{}}"""
                    "decks.list" -> """[{"id":"1","name":"Default","kind":"normal","presetId":"1"}]"""
                    "noteTypes.list" -> """[]"""
                    "notes.create" -> """{"noteId":"4321","cardIds":["4322"]}"""
                    else -> """{}"""
                },
            )
        }

        val client = AnkiConnect(url)
        assertEquals(1, client.version())
        client.ensureDeck("Yabai")
        client.ensureModel("Yabai", AnkiFields.ORDER)
        assertEquals(4321L, client.addNote("Yabai", "Yabai", note()))

        assertEquals(
            listOf(
                "api.describe",
                "decks.list",
                "decks.create",
                "noteTypes.list",
                "noteTypes.create",
                "notes.create",
            ),
            requests.map { it.get("method").asString },
        )
        assertEquals("the legacy path was probed again", 1, paths.count { it == "/" })
        assertEquals("2.0", requests.first().get("jsonrpc").asString)
        assertTrue("the request carries no id", requests.first().get("id").asInt > 0)

        val create = requests.last().getAsJsonObject("params")
        assertEquals("Yabai", create.getAsJsonObject("deck").get("name").asString)
        assertEquals(AnkiFields.ORDER.toSet(), create.getAsJsonObject("fields").keySet())
        assertTrue(
            "the v1 card would be allowed to duplicate",
            !create.getAsJsonObject("duplicates").get("allow").asBoolean,
        )
    }

    @Test
    fun `a v1 duplicate is a duplicate and the key travels in the header`() {
        val url = startV1 { method, json ->
            if (method == "api.describe") {
                envelope(
                    json,
                    """{"apiMajor":1,"ankiVersion":"25.02","schema":{},"methods":[],"limits":{}}""",
                )
            } else {
                """{"jsonrpc":"2.0","id":${json.get("id").asInt},"error":{"code":1002,""" +
                    """"message":"Duplicate note.","data":{"type":"validationFailed"}}}"""
            }
        }

        val client = AnkiConnect(url, apiKey = "segredo")
        client.version()
        val failure = runCatching { client.addNote("Yabai", "Yabai", note()) }
            .exceptionOrNull() as AnkiException

        assertEquals(AnkiFailure.DUPLICATE, failure.failure)
        assertTrue(
            "the API key did not travel in the Authorization header: $authorizations",
            authorizations.isNotEmpty() && authorizations.all { it == "Bearer segredo" },
        )
    }

    @Test
    fun `a v1 probe that also fails keeps the add-on missing reason`() {
        val url = startV1 { _, json ->
            """{"jsonrpc":"2.0","id":${json.get("id").asInt},"error":{"code":-32601,""" +
                """"message":"Method not found."}}"""
        }

        val failure = runCatching { AnkiConnect(url).version() }.exceptionOrNull() as AnkiException

        assertEquals(AnkiFailure.ADDON_MISSING, failure.failure)
    }

    @Test
    fun `a closed port is told apart from a missing add-on`() {
        val url = start { _, _ -> """{"result":6,"error":null}""" }
        server!!.stop(0)

        val failure = runCatching { AnkiConnect(url).version() }.exceptionOrNull() as AnkiException

        assertEquals(AnkiFailure.NOT_RUNNING, failure.failure)
    }
}
