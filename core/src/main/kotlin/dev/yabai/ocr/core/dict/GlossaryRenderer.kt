package dev.yabai.ocr.core.dict

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * Flattens a Yomitan glossary into plain text.
 *
 * Modern Yomitan dictionaries (JMdict/Jitendex) no longer store definitions as strings: every sense
 * is a tree of `structured-content` nodes with tags, styles and semantic markers. Nothing readable
 * comes out of it without a walk, so this renders the tree while dropping the parts that would only
 * be noise in a phone-sized panel (conjugation tables, images, furigana readings, Wiktionary
 * backlinks).
 */
object GlossaryRenderer {

    /** Semantic blocks that are pure noise when you just want to know what a word means. */
    private val SKIPPED_DATA = setOf(
        "forms", // conjugation tables
        "examples", // example sentences, only present in some builds
        "backlink", // Wiktionary's "Wiktionary" link
        "script", // which writing systems the word uses
        "attribution",
        "source",
        "categories",
    )

    /** Blocks that are worth keeping but should not be numbered as a sense. */
    private val MARKERS = mapOf(
        "infoGlossary" to "ℹ ",
        "references" to "→ ",
        "xref" to "→ ",
        "antonyms" to "≠ ",
        "related" to "≈ ",
        "tags" to "",
    )

    /** One rendered sense: numbered senses get "1." style prefixes, notes get their own marker. */
    data class Block(val numbered: Boolean, val text: String)

    /** Renders a whole glossary array, one [Block] per element. */
    fun renderGlossary(glossary: JsonArray, sensesLimit: Int = 24): List<Block> {
        val blocks = ArrayList<Block>(glossary.size())
        for (element in glossary) {
            if (blocks.size >= sensesLimit) break
            val block = renderElement(element) ?: continue
            if (block.text.isBlank()) continue
            blocks.add(block)
        }
        return blocks
    }

    /** Renders one glossary element, or null when it carries nothing worth showing. */
    fun renderElement(element: JsonElement): Block? {
        val (marker, node) = unwrap(element) ?: return null
        val text = render(node, depth = 0)?.trim()?.replace(Regex("\n{3,}"), "\n\n") ?: return null
        if (text.isBlank()) return null

        val prefix = MARKERS[marker]
        return if (prefix != null) {
            Block(numbered = false, text = prefixLines(text, prefix))
        } else {
            Block(numbered = true, text = text)
        }
    }

    /**
     * Peels off the `structured-content` wrapper and reports the semantic marker of the root list.
     * Returns `(marker or null, node)`.
     */
    private fun unwrap(element: JsonElement): Pair<String?, JsonElement>? {
        if (element.isJsonPrimitive) return Pair(null, element)
        if (element.isJsonArray) return Pair(null, element)
        if (!element.isJsonObject) return null

        val obj = element.asJsonObject
        when (obj.stringOrNull("type")) {
            "structured-content" -> {
                val content = obj.get("content") ?: return null
                return Pair(markerOf(content), content)
            }

            "image" -> return null
            "text" -> return Pair(null, JsonPrimitive(obj.stringOrNull("text") ?: ""))
        }
        return Pair(markerOf(element), element)
    }

    private fun markerOf(node: JsonElement): String? {
        if (!node.isJsonObject) return null
        val obj = node.asJsonObject
        if (obj.stringOrNull("tag") !in listOf("ul", "ol", "div")) return null
        return obj.getAsJsonObject("data")?.stringOrNull("content")
    }

    /**
     * Recursive text extraction.
     *
     * [depth] decides whether a list gets bullet markers. The outermost list of a sense is left
     * unmarked on purpose: the panel already numbers senses, and "1. • to eat" is worse than
     * "1. to eat". Deeper lists are genuine sub-lists and do get bullets.
     */
    private fun render(node: JsonElement?, depth: Int): String? {
        if (node == null || node.isJsonNull) return null

        if (node.isJsonPrimitive) {
            val primitive = node.asJsonPrimitive
            return when {
                primitive.isString -> primitive.asString
                primitive.isNumber -> primitive.asNumber.toString()
                else -> null
            }
        }

        if (node.isJsonArray) {
            val parts = node.asJsonArray.mapNotNull { render(it, depth) }.filter { it.isNotEmpty() }
            return if (parts.isEmpty()) null else parts.joinToString("")
        }

        val obj = node.asJsonObject

        when (obj.stringOrNull("type")) {
            "structured-content" -> return render(obj.get("content"), depth)
            "image" -> return null
            "text" -> return obj.stringOrNull("text")
        }

        val data = obj.getAsJsonObject("data")?.stringOrNull("content")
        if (data != null && data in SKIPPED_DATA) return null

        return when (val tag = obj.stringOrNull("tag")) {
            null -> render(obj.get("content"), depth)
            "br" -> "\n"
            "img" -> null
            "rp" -> null // parenthesis fallback around furigana readings
            "rt" -> null // the furigana reading itself
            "ruby" -> renderChildren(obj, separator = "", depth = depth) // base text only
            "ul", "ol" -> renderList(obj, depth)
            "li" -> renderChildren(obj, separator = "", depth = depth)
            "table" -> renderTable(obj, depth)
            "tr" -> renderChildren(obj, separator = " | ", depth = depth)
            "td", "th" -> renderChildren(obj, separator = " ", depth = depth)
            "div" -> renderChildren(obj, separator = "\n", depth = depth)
            "span", "a", "code", "em", "strong", "b", "i", "u", "sup", "sub" ->
                renderChildren(obj, separator = "", depth = depth)

            else -> if (tag != null) renderChildren(obj, separator = "", depth = depth) else null
        }
    }

    private fun renderChildren(obj: JsonObject, separator: String, depth: Int): String? {
        val content = obj.get("content") ?: return null
        if (content.isJsonArray) {
            val parts = content.asJsonArray.mapNotNull { render(it, depth) }.filter { it.isNotEmpty() }
            return if (parts.isEmpty()) null else parts.joinToString(separator)
        }
        return render(content, depth)
    }

    /**
     * Lists become one line per item. A list holding a single item is unwrapped, because that is how
     * Yomitan wraps every sense and an extra bullet on every definition is just clutter.
     */
    private fun renderList(obj: JsonObject, depth: Int): String? {
        val raw = obj.get("content")
            ?: return renderChildren(obj, separator = "\n", depth = depth)

        val items = if (raw.isJsonArray) {
            raw.asJsonArray.mapNotNull { render(it, depth + 1)?.trim() }.filter { it.isNotEmpty() }
        } else {
            listOfNotNull(render(raw, depth + 1)?.trim()).filter { it.isNotEmpty() }
        }

        if (items.isEmpty()) return null
        if (items.size == 1) return items[0]

        val isOrdered = obj.stringOrNull("tag") == "ol"
        return items.mapIndexed { index, item ->
            // The outermost list carries no markers: the panel already numbers senses, and
            // "1. • to eat" reads worse than "1. to eat".
            val prefix = when {
                depth == 0 -> ""
                isOrdered -> "${index + 1}. "
                else -> "• "
            }
            prefix + indentContinuation(item, prefix.length)
        }.joinToString("\n")
    }

    private fun renderTable(obj: JsonObject, depth: Int): String? {
        val rows = obj.get("content") as? JsonArray ?: return renderChildren(obj, "\n", depth)
        val lines = rows.mapNotNull { row ->
            val rowObject = row.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val cells = (rowObject.get("content") as? JsonArray)
                ?.mapNotNull { render(it, depth + 1)?.trim() }
                ?.filter { it.isNotEmpty() }
                ?: return@mapNotNull null
            if (cells.isEmpty()) null else cells.joinToString(" | ")
        }
        return if (lines.isEmpty()) null else lines.joinToString("\n")
    }

    /** Prefixes every line of a multi-line block, so notes stay aligned. */
    private fun prefixLines(text: String, prefix: String): String =
        text.lineSequence().joinToString("\n") { if (it.isEmpty()) it else prefix + it }

    /** Hanging indents: a wrapped bullet keeps its text aligned under the first character. */
    private fun indentContinuation(text: String, prefixLength: Int): String {
        if (prefixLength == 0) return text
        val pad = " ".repeat(prefixLength)
        return text.replace("\n", "\n$pad")
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        return if (primitive.isString) primitive.asString else null
    }
}
