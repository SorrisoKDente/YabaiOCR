package dev.yabai.ocr.core.dict

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Turns an inflected Japanese word back into the forms a dictionary actually stores.
 *
 * Japanese dictionaries list 食べる, never 食べました, so a raw lookup on manga text misses almost
 * every verb and adjective. This walks the rule table shipped in `deinflect.json` (the one Yomichan
 * and Yomitan use) and produces every base form that could have produced the input.
 *
 * The rule table is a *matrix*: each rule also carries which part-of-speech tags are allowed to
 * start from (`rulesIn`) and which the result should have (`rulesOut`). Those are bitmasks, and they
 * are what stops 「はし」 from being "deinflected" into nonsense: a candidate is only accepted later
 * if the dictionary entry it lands on declares a matching tag.
 */
class Deinflector private constructor(
    private val groups: List<Group>,
    /** Rules bucketed by the last character of `kanaIn`, so a probe touches a handful, not all 564. */
    private val byLastChar: Map<Char, List<IndexedRule>>,
) {

    data class Group(val reason: String, val rules: List<Rule>)

    data class Rule(val kanaIn: String, val kanaOut: String, val rulesIn: Int, val rulesOut: Int)

    private data class IndexedRule(val group: Group, val rule: Rule)

    /**
     * One reachable base form.
     *
     * [rules] is the bitmask of part-of-speech tags the dictionary entry must declare for this
     * candidate to count; `0` means "nothing was rewritten, accept anything".
     */
    data class Result(val term: String, val rules: Int, val reasons: List<String>)

    fun deinflect(source: String): List<Result> {
        val results = ArrayList<Result>(16)
        // Keyed by text+mask because the same surface can be reached through different rule chains.
        val seen = HashSet<String>(64)
        results.add(Result(source, 0, emptyList()))
        seen.add("$source\u0000-")

        var i = 0
        while (i < results.size && results.size < MAX_RESULTS) {
            val current = results[i]
            i++

            if (current.term.length > MAX_TERM_LENGTH) continue

            val candidates = byLastChar[current.term.lastOrNull() ?: continue] ?: continue
            for (indexed in candidates) {
                val rule = indexed.rule
                if (current.rules != 0 && (current.rules and rule.rulesIn) == 0) continue
                if (!current.term.endsWith(rule.kanaIn)) continue
                val newLength = current.term.length - rule.kanaIn.length + rule.kanaOut.length
                if (newLength <= 0) continue

                val next = current.term.substring(0, current.term.length - rule.kanaIn.length) + rule.kanaOut
                val key = "$next\u0000${rule.rulesOut}"
                if (!seen.add(key)) continue

                results.add(Result(next, rule.rulesOut, listOf(indexed.group.reason) + current.reasons))
            }
        }
        return results
    }

    /** Everything after the first entry, which is the input itself. */
    fun baseForms(source: String): List<Result> =
        if (source.isEmpty()) emptyList() else deinflect(source).drop(1)

    companion object {
        private const val MAX_RESULTS = 96
        private const val MAX_TERM_LENGTH = 24

        /** Part-of-speech tags that participate in deinflection, and their bit. */
        private val RULE_TYPES: Map<String, Int> = mapOf(
            "v1" to 0b0000001, // ichidan verb
            "v5" to 0b0000010, // godan verb
            "vs" to 0b0000100, // suru verb
            "vk" to 0b0001000, // kuru verb
            "vz" to 0b0010000, // zuru verb
            "adj-i" to 0b0100000, // i-adjective
            "iru" to 0b1000000, // intermediate -iru ending (progressive/perfect)
        )

        /** Turns a dictionary entry's `rules` field ("v1 v5r adj-i") into the matching bitmask. */
        fun flagsOf(rules: String): Int {
            if (rules.isEmpty()) return 0
            var flags = 0
            var start = 0
            while (start < rules.length) {
                var end = start
                while (end < rules.length && rules[end] != ' ') end++
                flags = flags or (RULE_TYPES[rules.substring(start, end)] ?: 0)
                start = end + 1
            }
            return flags
        }

        /** True if a dictionary entry carrying [entryFlags] can be the base of a [candidateFlags] hit. */
        fun compatible(entryFlags: Int, candidateFlags: Int): Boolean =
            candidateFlags == 0 || (entryFlags and candidateFlags) != 0

        @Volatile
        private var cached: Deinflector? = null

        /** Loads the rule table that ships inside this module. */
        fun get(): Deinflector = cached ?: synchronized(this) {
            cached ?: load().also { cached = it }
        }

        fun load(): Deinflector {
            val stream = Deinflector::class.java.getResourceAsStream("/deinflect.json")
                ?: error("deinflect.json missing from the core module resources")
            val root: JsonObject = stream.use { JsonParser.parseReader(it.reader()).asJsonObject }

            val groups = ArrayList<Group>(64)
            val byLastChar = HashMap<Char, MutableList<IndexedRule>>(128)

            for ((reason, element) in root.entrySet()) {
                val rules = ArrayList<Rule>()
                for (ruleElement in element.asJsonArray) {
                    val rule = ruleElement.asJsonObject
                    val kanaIn = rule.get("kanaIn").asString
                    if (kanaIn.isEmpty()) continue

                    val parsed = Rule(
                        kanaIn = kanaIn,
                        kanaOut = rule.get("kanaOut").asString,
                        rulesIn = flagsOf(rule.getAsJsonArray("rulesIn").joinToString(" ") { it.asString }),
                        rulesOut = flagsOf(rule.getAsJsonArray("rulesOut").joinToString(" ") { it.asString }),
                    )
                    rules.add(parsed)
                }
                if (rules.isEmpty()) continue

                val group = Group(reason, rules)
                groups.add(group)
                for (rule in rules) {
                    byLastChar.getOrPut(rule.kanaIn.last()) { ArrayList(8) }.add(IndexedRule(group, rule))
                }
            }

            return Deinflector(groups, byLastChar)
        }
    }
}
