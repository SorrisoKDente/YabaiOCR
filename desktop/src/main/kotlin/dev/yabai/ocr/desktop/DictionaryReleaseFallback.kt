package dev.yabai.ocr.desktop

import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Reading of GitHub release assets, shared by the installer and the update check.
 *
 * The JMdict family (`yomidevs/jmdict-yomitan`) publishes a dated release every day and some of those
 * builds fail upstream: the assets are there, but they carry only `index.json` and a tag bank — 2 KB
 * with no term or kanji bank, titled «JMdict [unknown]». Importing one ends with «Nenhum verbete
 * encontrado», which looks like our bug, and comparing against it would offer an update forever.
 *
 * So both sides agree on the same rule: the newest publication whose asset is big enough to be a
 * real dictionary. [dictionaryDownloadUrls] appends the older ones as fallbacks.
 */

/** A Yomitan bank file smaller than this cannot hold a usable dictionary. */
internal const val YOMITAN_MIN_BANK_BYTES = 2048L

/** Below this, a release asset is one of the broken upstream builds (the real ones are 0.7–16 MB). */
internal const val GITHUB_RELEASE_MIN_ASSET_BYTES = 64L * 1024

private val GITHUB_RELEASE_DOWNLOAD = Regex(
    """^https://github\.com/([^/]+/[^/]+)/releases/(?:latest/download|download/[^/]+)/([^/?]+)$""",
)
private val RELEASE_TAG_IN_URL = Regex("""/releases/download/([^/]+)/""")

/** One downloadable publication of an asset. */
internal data class ReleasePublication(val tag: String, val name: String, val size: Long, val url: String)

/** `owner/repo` and asset name of a GitHub release download URL, or null for anything else. */
internal fun releaseDownloadTarget(url: String): Pair<String, String>? {
    val match = GITHUB_RELEASE_DOWNLOAD.matchEntire(url) ?: return null
    val repo = match.groupValues[1]
    val asset = match.groupValues[2]
    return if (repo.isBlank() || asset.isBlank()) null else repo to asset
}

/** Release tag of a `/releases/download/<tag>/<asset>` URL, or null for a `latest` URL. */
internal fun releaseTagFromUrl(url: String): String? =
    RELEASE_TAG_IN_URL.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() && it != "latest" }

/** Where the release list for [repo] lives. Unauthenticated: 60 requests an hour is plenty. */
internal fun releasesApiUrl(repo: String): String =
    "https://api.github.com/repos/$repo/releases?per_page=20"

/**
 * Publications of [assetName] that can hold a dictionary, newest first.
 *
 * Releases without the asset are skipped, and so are the stub assets [minBytes] rules out, so no
 * caller downloads a build that is known to be empty.
 */
internal fun usableReleaseAssets(
    releasesJson: String?,
    assetName: String,
    minBytes: Long = GITHUB_RELEASE_MIN_ASSET_BYTES,
): List<ReleasePublication> {
    val releases = releasesJson
        ?.let { runCatching { JsonParser.parseString(it).asJsonArray }.getOrNull() }
        ?: return emptyList()

    return releases.mapNotNull { element ->
        val release = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
        val tag = runCatching { release.get("tag_name").asString }.getOrNull().orEmpty()
        if (tag.isBlank()) return@mapNotNull null
        val assets = runCatching { release.getAsJsonArray("assets") }.getOrNull() ?: return@mapNotNull null
        val asset = assets.firstOrNull { candidate ->
            runCatching { candidate.asJsonObject.get("name").asString == assetName }.getOrDefault(false)
        }?.asJsonObject ?: return@mapNotNull null

        val size = runCatching { asset.get("size").asLong }.getOrDefault(0L)
        if (size < minBytes) return@mapNotNull null
        val url = runCatching { asset.get("browser_download_url").asString }.getOrNull().orEmpty()
        if (url.isBlank()) null else ReleasePublication(tag, assetName, size, url)
    }
}

/**
 * URLs to try, in order: [primary] first, then the previous publications with a usable asset.
 *
 * [releasesJson] is the release list of the same repository; null (offline, or a URL that is not a
 * GitHub release) leaves only the primary, which is what every other source did before.
 */
internal fun dictionaryDownloadUrls(
    primary: String,
    releasesJson: String?,
    limit: Int = 5,
): List<String> {
    val asset = releaseDownloadTarget(primary)?.second ?: return listOf(primary)
    val fallbacks = usableReleaseAssets(releasesJson, asset).map { it.url }.filter { it != primary }
    return listOf(primary) + fallbacks.take(limit)
}

/**
 * Whether [zipFile] is a Yomitan archive that carries entries.
 *
 * A usable archive has at least one `term_bank_*.json` or `kanji_bank_*.json` big enough to hold a
 * record; the upstream stubs have neither. An unreadable ZIP counts as unusable — nothing downstream
 * could do anything with it either.
 */
internal fun yomitanArchiveHasEntries(zipFile: File): Boolean {
    if (!zipFile.isFile || zipFile.length() == 0L) return false
    return try {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().any { entry ->
                val base = entry.name.substringAfterLast('/')
                val bank = base.startsWith("term_bank_") || base.startsWith("kanji_bank_")
                !entry.isDirectory && bank && base.endsWith(".json") && entry.size >= YOMITAN_MIN_BANK_BYTES
            }
        }
    } catch (_: IOException) {
        false
    }
}

/**
 * Newest publication of the asset behind a GitHub release URL, or null when it is not one.
 *
 * [fetch] returns the body of a URL (null on failure), so callers keep their own HTTP client and the
 * release list is fetched at most once per repository per check.
 */
internal fun newestReleaseFor(url: String, fetch: (String) -> String?): ReleasePublication? {
    val asset = releaseDownloadTarget(url)?.second ?: return null
    return usableReleaseAssets(fetch(releasesApiUrl(releaseDownloadTarget(url)!!.first)), asset).firstOrNull()
}

/**
 * Whether the installed dictionary is older than [remote].
 *
 * The release tag is the answer when both sides have one: a fallback install of `2026-11-13` must not
 * be offered «update to 2026-11-14» while that publication is an empty stub. Installs made before the
 * tag was recorded are compared by size, the only field both sides always had.
 */
internal fun dictionaryUpdateAvailable(
    installed: DictionarySignature?,
    remote: ReleasePublication,
): Boolean {
    if (installed == null) return true
    val localTag = installed.releaseTag
    if (localTag != null) return localTag != remote.tag
    return installed.length != remote.size
}
