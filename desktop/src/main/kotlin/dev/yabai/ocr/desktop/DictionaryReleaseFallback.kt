package dev.yabai.ocr.desktop

import com.google.gson.JsonParser
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipException

/**
 * Fallback for dictionary releases whose newest publication is empty.
 *
 * The JMdict family (JMdict, JMnedict, KANJIDIC at `yomidevs/jmdict-yomitan`) publishes a dated
 * release every day, and some of those builds fail upstream: the assets are still there, but they
 * carry only `index.json` and a tag bank — 2 KB with no term or kanji bank at all, titled
 * «JMdict [unknown]». The compiler then refuses them with «Nenhum verbete encontrado», which reads
 * like the app's bug to anyone installing a dictionary for the first time.
 *
 * So the installer downloads the requested asset, checks that it really holds entries, and walks
 * back to the previous release (GitHub's `releases` API, newest first) when it does not.
 */

/** A Yomitan bank file smaller than this cannot hold a usable dictionary. */
internal const val YOMITAN_MIN_BANK_BYTES = 2048L

/**
 * Releases whose asset is smaller than this are the broken upstream builds; the real assets of that
 * repository are 0.7–16 MB, so the check never hides a working publication.
 */
internal const val GITHUB_RELEASE_MIN_ASSET_BYTES = 64L * 1024

private val GITHUB_RELEASE_DOWNLOAD = Regex(
    """^https://github\.com/([^/]+/[^/]+)/releases/(?:latest/download|download/[^/]+)/([^/?]+)$""",
)

/** `owner/repo` and asset name of a GitHub release download URL, or null for anything else. */
internal fun releaseDownloadTarget(url: String): Pair<String, String>? {
    val match = GITHUB_RELEASE_DOWNLOAD.matchEntire(url) ?: return null
    val repo = match.groupValues[1]
    val asset = match.groupValues[2]
    if (repo.isBlank() || asset.isBlank()) return null
    return repo to asset
}

/** Where the release list for [repo] lives. Unauthenticated: 60 requests an hour is plenty. */
internal fun releasesApiUrl(repo: String): String =
    "https://api.github.com/repos/$repo/releases?per_page=20"

/**
 * Download URLs of the previous publications of [assetName], newest first.
 *
 * Releases without that asset are skipped, and so are the stub assets [GITHUB_RELEASE_MIN_ASSET_BYTES]
 * rules out, so the caller never spends a download on a build that is known to be empty. The primary
 * URL is not part of the result: the caller already tried it.
 */
internal fun fallbackAssetUrls(
    releasesJson: String,
    assetName: String,
    limit: Int = 5,
    minBytes: Long = GITHUB_RELEASE_MIN_ASSET_BYTES,
): List<String> {
    if (limit <= 0) return emptyList()
    val releases = runCatching { JsonParser.parseString(releasesJson).asJsonArray }.getOrNull()
        ?: return emptyList()

    val urls = mutableListOf<String>()
    for (element in releases) {
        val release = runCatching { element.asJsonObject }.getOrNull() ?: continue
        val assets = runCatching { release.getAsJsonArray("assets") }.getOrNull() ?: continue
        val asset = assets.firstOrNull { candidate ->
            runCatching { candidate.asJsonObject.get("name").asString == assetName }.getOrDefault(false)
        }?.asJsonObject ?: continue

        val size = runCatching { asset.get("size").asLong }.getOrDefault(0L)
        if (size < minBytes) continue
        val url = runCatching { asset.get("browser_download_url").asString }.getOrNull() ?: continue
        if (url.isBlank() || urls.contains(url)) continue
        urls += url
        if (urls.size == limit) break
    }
    return urls
}

/**
 * Whether [zipFile] is a Yomitan archive that carries entries.
 *
 * A usable archive has at least one `term_bank_*.json` or `kanji_bank_*.json` big enough to hold a
 * record. The upstream stub releases have neither: only `index.json` and `tag_bank_*.json`, which is
 * why importing them ended with «Nenhum verbete encontrado».
 *
 * An unreadable ZIP counts as unusable: nothing downstream could do anything with it either.
 */
internal fun yomitanArchiveHasEntries(zipFile: File): Boolean {
    if (!zipFile.isFile || zipFile.length() == 0L) return false
    return try {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().any { entry ->
                if (entry.isDirectory) return@any false
                val base = entry.name.substringAfterLast('/')
                val bank = base.startsWith("term_bank_") || base.startsWith("kanji_bank_")
                bank && base.endsWith(".json") && entry.size >= YOMITAN_MIN_BANK_BYTES
            }
        }
    } catch (_: ZipException) {
        false
    } catch (_: java.io.IOException) {
        false
    }
}

/**
 * URLs to try, in order, for a catalog entry whose download lives on a GitHub release.
 *
 * [releasesJson] is the release list of the same repository (null when it could not be fetched, or
 * when the URL is not a GitHub release at all): without it the result is only the primary URL, which
 * keeps every other source working exactly as before.
 */
internal fun dictionaryDownloadUrls(primary: String, releasesJson: String?): List<String> {
    val target = releaseDownloadTarget(primary) ?: return listOf(primary)
    val (repo, asset) = target
    val fallbacks = releasesJson
        ?.let { fallbackAssetUrls(it, asset) }
        .orEmpty()
        .filter { it != primary }
    return listOf(primary) + fallbacks
}
