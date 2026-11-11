package dev.yabai.ocr.core.dict

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Flattens a Yomitan glossary into the text the panel shows.
 *
 * Modern Yomitan dictionaries (JMdict/Jitendex) no longer store definitions as strings: every sense
 * is a tree of `structured-content` nodes with tags, styles and semantic markers. Nothing readable
 * comes out of it without a walk, so this renders the tree while dropping the parts that would only
 * be noise in a phone-sized panel (conjugation tables, images, furigana readings, Wiktionary
 * backlinks).
 *
 * The rendered text is a small grammar the panel knows how to draw (R19): a **sense** is a line
 * starting with `1. `, a **note** starts with `ℹ`, an **example** with `例` and its translation with
 * `→`. Nothing is glued to anything else: Jitendex keeps part-of-speech tags, the definition, a note
 * and two example sentences inside one list item, and joining them the way the markup does reads
 * `1-danintransitive1. to be called outNote passive form of 呼ぶ`.
 *
 * Tags found on the way out are reported separately ([Rendered.tags]) instead of being written into
 * the definition: the panel shows them under the headword, where the eye looks for them.
 */
object GlossaryRenderer {

    /** One rendered piece of a glossary: a numbered sense, or a note/example that carries its mark. */
    data class Block(val numbered: Boolean, val text: String)

    /** Everything a glossary array rendered to. */
    class Rendered(val blocks: List<Block>, val tags: String)

    /** What a note line starts with. The panel draws these smaller and greyer. */
    const val NOTE_MARK = "ℹ "

    /** What an example sentence starts with. */
    const val EXAMPLE_MARK = "例 "

    /** What the translation of the example above it starts with. */
    const val TRANSLATION_MARK = "→ "

    /** Semantic blocks that are pure noise when you just want to know what a word means. */
    private val SKIPPED_DATA = setOf(
        "forms", // conjugation tables
        "examples", // example sentences, only present in some builds
        "backlink", // Wiktionary's "Wiktionary" link
        "script", // which writing systems the word uses
        "attribution",
        "source",
        "categories",
        // Jitendex's own furniture: the label of a note ("Note") and the Tatoeba footnote numbers.
        "extra-label",
        "sense-note-label",
        "attribution-footnote",
        // Cross-references ("Ver também") are links to another dictionary entry. The panel has no
        // way to follow them, so the label, the linked headword and its gloss are only noise (R42).
        "xref",
        "xref-content",
        "xref-glossary",
        "reference-label",
    )

    private val TAG_DATA_SET = setOf(
        "part-of-speech-info",
        "dialect-info",
        "field-info",
        "misc-info",
        "grammar",
        "grammar-info",
        "part-of-speech",
        "pos",
        "word-class",
        "type",
        "sense-type",
    )

    private val TAG_CLASS_SET = setOf("tag", "grammar", "pos", "word-class")

    /** Blocks that are worth keeping but should not be numbered as a sense. */
    private val MARKERS = mapOf(
        "infoGlossary" to NOTE_MARK,
        "references" to "→ ",
        "antonyms" to "≠ ",
        "related" to "≈ ",
        "tags" to "",
    )

    /** Elements that start a line of their own, as opposed to running on inside a phrase. */
    private val BLOCK_TAGS = setOf("div", "ul", "ol", "li", "table", "p", "h1", "h2", "h3", "h4")

    /** The lists a dictionary uses to hold senses. */
    private val LIST_TAGS = setOf("ul", "ol")

    /** Renders a whole glossary array, one [Block] per sense and per note or example. */
    fun renderGlossary(glossary: JsonArray, sensesLimit: Int = 24): Rendered {
        val sink = Sink(sensesLimit)
        for (element in glossary) {
            if (sink.full) break
            sink.addTop(element)
        }
        return Rendered(sink.blocks, sink.tags.joinToString("・"))
    }

    /** Renders one glossary element on its own, for callers that have just one. */
    fun renderElement(element: JsonElement): List<Block> {
        val sink = Sink(sensesLimit = Int.MAX_VALUE)
        sink.addTop(element)
        return sink.blocks
    }

    /**
     * Numbers the senses and puts the notes and examples in their own lines: this is the text the
     * dictionary stores, and the one the panel draws.
     */
    fun renderDefinition(blocks: List<Block>): String {
        val out = StringBuilder()
        var sense = 0
        for (block in blocks) {
            if (block.text.isBlank()) continue
            if (out.isNotEmpty()) out.append('\n')
            if (!block.numbered) {
                out.append(block.text)
                continue
            }
            sense++
            val prefix = "$sense. "
            out.append(prefix).append(block.text.replace("\n", "\n" + " ".repeat(prefix.length)))
        }
        return out.toString()
    }

    /** Collects the blocks of one glossary array, keeping the tag list and the sense count. */
    private class Sink(private val sensesLimit: Int) {

        val blocks = ArrayList<Block>()
        val tags = LinkedHashSet<String>()
        private var senses = 0

        val full: Boolean get() = senses >= sensesLimit

        /** One element of the top-level array: a plain sense, a list of senses, or a marked note. */
        fun addTop(element: JsonElement) {
            if (full) return
            val node = peel(element) ?: return

            // A `structured-content` can hold more than one thing — Jitendex puts the entry and its
            // attribution side by side — so each part is looked at on its own.
            if (node.isJsonArray) {
                node.asJsonArray.forEach { addTop(it) }
                return
            }

            val senseItems = senseItems(node)
            if (senseItems == null) {
                addPlain(element, node)
                return
            }
            collectTags(node)
            for (sense in senseItems) {
                if (full) break
                addSense(sense)
            }
        }

        /** A glossary element that is one block: a string, a paragraph, or a marked note. */
        private fun addPlain(element: JsonElement, node: JsonElement) {
            val marker = markerOf(element) ?: markerOf(node)
            // A glossary list on its own is one sense with several definitions, and those read as a
            // series — "to eat; to consume" — not as one per line.
            if (marker == null || marker == "glossary") {
                val gloss = glossaryText(node).trim()
                if (gloss.isNotEmpty() && isGlossary(node)) {
                    senses++
                    blocks += Block(numbered = true, text = gloss)
                    return
                }
            }
            val text = render(node, depth = 0)?.trim()?.replace(Regex("\n{3,}"), "\n\n") ?: return
            if (text.isBlank()) return
            val prefix = marker?.let { MARKERS[it] }
            if (prefix != null) {
                blocks += Block(numbered = false, text = prefixLines(text, prefix))
            } else {
                senses++
                blocks += Block(numbered = true, text = text)
            }
        }

        /** One sense: its definitions, then whatever notes and examples hang off it. */
        private fun addSense(sense: JsonObject) {
            val body = ArrayList<Block>()
            val pending = StringBuilder()
            val senseTags = ArrayList<String>()

            fun flush() {
                val text = pending.toString().trim()
                pending.setLength(0)
                if (text.isEmpty()) return
                val head = if (senseTags.isEmpty()) text else "［${senseTags.joinToString("・")}］ $text"
                body += Block(numbered = true, text = head)
                senses++
            }

            fun handle(child: JsonElement) {
                val obj = child as? JsonObject
                when {
                    isTag(child) -> tagText(child)?.let { senseTags += it }

                    isContainer(child) -> children(obj!!).forEach { handle(it) }

                    isGlossary(child) -> {
                        val gloss = glossaryText(child)
                        if (gloss.isNotEmpty()) {
                            if (pending.isNotEmpty()) pending.append(' ')
                            pending.append(gloss)
                        }
                    }

                    isNote(child) -> {
                        flush()
                        noteText(child)?.let { body += Block(numbered = false, text = prefixLines(it, NOTE_MARK)) }
                    }

                    isExample(child) -> {
                        flush()
                        exampleText(child)?.let { body += Block(numbered = false, text = it) }
                    }

                    obj != null && children(obj).any { isNote(it) || isExample(it) || isContainer(it) } ->
                        children(obj).forEach { handle(it) }

                    else -> {
                        // Anything else the dictionary put in the sense — an inline phrase, a
                        // sub-list — is text of the definition, not a block of its own.
                        val text = render(child, depth = 0)?.trim()
                        if (!text.isNullOrEmpty()) {
                            if (pending.isNotEmpty()) pending.append('\n')
                            pending.append(text)
                        }
                    }
                }
            }

            children(sense).forEach { handle(it) }
            flush()
            blocks += body
        }

        /** The tags of the whole word: they sit beside the list of senses, never inside one. */
        private fun collectTags(node: JsonElement) {
            val group = node as? JsonObject ?: return
            for (child in children(group)) {
                if (isTag(child)) {
                    tagText(child)?.let { tags += it }
                } else {
                    val childObject = child as? JsonObject ?: continue
                    if (childObject.stringOrNull("tag") == "li") collectTags(childObject)
                }
            }
        }
    }

    /** Peels the wrappers between the array element and the node that says what it is. */
    private fun peel(element: JsonElement): JsonElement? {
        var node = element
        while (true) {
            node = when {
                node.isJsonObject && node.asJsonObject.stringOrNull("type") == "structured-content" ->
                    node.asJsonObject.get("content") ?: return null

                node.isJsonArray && node.asJsonArray.size() == 1 -> node.asJsonArray.get(0)
                else -> return node
            }
        }
    }

    /**
     * The senses inside [node], when it is a list of them — Jitendex wraps the whole entry in
     * `<ul data-content="sense-groups"><li data-content="sense-group"><ol>…`. Returns null when the
     * node is an ordinary definition, so the caller renders it the plain way.
     */
    private fun senseItems(node: JsonElement): List<JsonObject>? {
        val obj = node as? JsonObject ?: return null
        if (obj.stringOrNull("tag") !in LIST_TAGS && dataContent(obj) != "sense-group") return null

        if (dataContent(obj) == "sense-group") {
            // Newer Jitendex does not wrap the group in a list: the tags and the senses are its
            // direct children (`div[data-content=sense]`). Returning null there made the whole
            // group render as plain text, with the part of speech numbered as if it were a meaning
            // and the `Ver também` link written out (R42).
            val direct = children(obj).mapNotNull { it as? JsonObject }
                .filter { dataContent(it) == "sense" }
            if (direct.isNotEmpty()) return direct

            val list = children(obj).mapNotNull { it as? JsonObject }
                .firstOrNull { it.stringOrNull("tag") in LIST_TAGS }
                ?: return null
            return senseItems(list)
        }

        val items = children(obj).mapNotNull { it as? JsonObject }
        if (items.isEmpty()) return null
        return when {
            items.all { dataContent(it) == "sense" } -> items
            items.any { dataContent(it) == "sense-group" } -> items.flatMap { senseItems(it).orEmpty() }
            else -> null
        }
    }

    private fun isGlossary(node: JsonElement): Boolean {
        val obj = node as? JsonObject ?: return false
        return dataContent(obj) == "glossary"
    }

    private fun isNote(node: JsonElement): Boolean {
        val obj = node as? JsonObject ?: return false
        return dataContent(obj) in setOf("note", "sense-note", "infoGlossary", "notes")
    }

    /** A wrapper that holds extra boxes (Jitendex's `extra-info`) and has no text of its own. */
    private fun isContainer(node: JsonElement): Boolean {
        val obj = node as? JsonObject ?: return false
        return dataContent(obj) == "extra-info"
    }

    private fun isExample(node: JsonElement): Boolean {
        val obj = node as? JsonObject ?: return false
        return dataContent(obj) in setOf("example-sentence", "example")
    }

    private fun isTag(node: JsonElement): Boolean {
        val obj = node as? JsonObject ?: return false
        val data = dataContent(obj)
        val clazz = className(obj)
        return data in TAG_DATA_SET || clazz in TAG_CLASS_SET
    }

    private fun tagText(node: JsonElement): String? =
        render(node, depth = 0)?.trim()?.takeIf { it.isNotEmpty() }

    /** The definitions of one sense, one line per definition, separated the way dictionaries do. */
    private fun glossaryText(node: JsonElement): String {
        val obj = node as? JsonObject ?: return render(node, depth = 0)?.trim().orEmpty()
        val items = children(obj).mapNotNull { render(it, depth = 0)?.trim() }.filter { it.isNotEmpty() }
        return items.joinToString("; ")
    }

    private fun noteText(node: JsonElement): String? =
        render(node, depth = 0)?.trim()?.takeIf { it.isNotEmpty() }

    /** An example is two lines: the sentence and its translation, each with its own mark. */
    private fun exampleText(node: JsonElement): String? {
        val obj = node as? JsonObject ?: return null
        val parts = children(obj).mapNotNull { it as? JsonObject }
        val japanese = parts.firstOrNull { dataContent(it) == "example-sentence-a" }
            ?: parts.firstOrNull()
        val translation = parts.firstOrNull { dataContent(it) == "example-sentence-b" }

        val sentence = japanese?.let { render(it, depth = 0)?.trim() } ?: return null
        if (sentence.isEmpty()) return null
        val meaning = translation?.let { render(it, depth = 0)?.trim() }
        return if (meaning.isNullOrEmpty()) {
            EXAMPLE_MARK + sentence
        } else {
            EXAMPLE_MARK + sentence + "\n" + TRANSLATION_MARK + meaning
        }
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
            return joinChildren(node.asJsonArray.toList(), depth)
        }

        val obj = node.asJsonObject

        when (obj.stringOrNull("type")) {
            "structured-content" -> return render(obj.get("content"), depth)
            "image" -> return null
            "text" -> return obj.stringOrNull("text")
        }

        val data = dataContent(obj)
        if (data != null && data in SKIPPED_DATA) return null

        return when (val tag = obj.stringOrNull("tag")) {
            null -> render(obj.get("content"), depth)
            "br" -> "\n"
            "img" -> null
            "rp" -> null // parenthesis fallback around furigana readings
            "rt" -> null // the furigana reading itself
            "ruby" -> renderChildren(obj, separator = "", depth = depth) // base text only
            "ul", "ol" -> renderList(obj, depth)
            "li" -> joinChildren(children(obj), depth)
            "table" -> renderTable(obj, depth)
            "tr" -> renderChildren(obj, separator = " | ", depth = depth)
            "td", "th" -> renderChildren(obj, separator = " ", depth = depth)
            "div" -> renderChildren(obj, separator = "\n", depth = depth)
            "span", "a", "code", "em", "strong", "b", "i", "u", "sup", "sub" ->
                renderChildren(obj, separator = "", depth = depth)

            else -> renderChildren(obj, separator = "", depth = depth)
        }
    }

    /**
     * Joins a list of children the way the markup means them: a block element starts a new line, an
     * inline one runs on inside the phrase.
     */
    private fun joinChildren(parts: List<JsonElement>, depth: Int): String? {
        val rendered = ArrayList<Pair<Boolean, String>>(parts.size)
        for (part in parts) {
            val text = render(part, depth) ?: continue
            if (text.isEmpty()) continue
            val block = part.isJsonObject && (
                part.asJsonObject.stringOrNull("tag") in BLOCK_TAGS ||
                    dataContent(part.asJsonObject) in setOf("extra-box", "example-sentence")
                )
            rendered += block to text
        }
        if (rendered.isEmpty()) return null
        val out = StringBuilder(rendered.first().second)
        for ((block, text) in rendered.drop(1)) {
            if (block) out.append('\n')
            out.append(text)
        }
        return out.toString()
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

    /** The children of a node: `content` is an array in most nodes and a single object in others. */
    private fun children(obj: JsonObject): List<JsonElement> {
        val content = obj.get("content") ?: return emptyList()
        return if (content.isJsonArray) content.asJsonArray.toList() else listOf(content)
    }

    private fun dataContent(obj: JsonObject): String? =
        obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.stringOrNull("content")

    private fun className(obj: JsonObject): String? =
        obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.stringOrNull("class")

    private fun markerOf(node: JsonElement): String? {
        if (!node.isJsonObject) return null
        val obj = node.asJsonObject
        if (obj.stringOrNull("tag") !in listOf("ul", "ol", "div")) return null
        return dataContent(obj)
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        if (!primitive.isString) return null
        return primitive.asString
    }
}
