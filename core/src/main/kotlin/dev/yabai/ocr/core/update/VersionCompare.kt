package dev.yabai.ocr.core.update

/**
 * Compares two release versions, dotted numbers only.
 *
 * Debug builds report `1.0.0-debug` and tags may carry a `v` prefix or a `+build` suffix; those are
 * the same release as `1.0.0`, not a newer one, so both are stripped before comparing. Missing
 * components count as zero, which is what makes `1.2` and `1.2.0` equal.
 *
 * Shared by the desktop and Android update checks so both agree on what «newer» means.
 */
fun compareVersions(first: String, second: String): Int {
    val a = normalize(first)
    val b = normalize(second)
    val max = maxOf(a.size, b.size)
    for (index in 0 until max) {
        val left = a.getOrElse(index) { 0 }
        val right = b.getOrElse(index) { 0 }
        if (left != right) return left.compareTo(right)
    }
    return 0
}

/** True when [latest] is newer than [current]. */
fun isNewerVersion(current: String, latest: String): Boolean = compareVersions(current, latest) < 0

private fun normalize(version: String): List<Int> =
    version.removePrefix("v").substringBefore('-').substringBefore('+')
        .split('.')
        .mapNotNull { it.trim().toIntOrNull() }
