package dev.yabai.ocr.core.lens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LensOptions(
    /** Locale advertised to Lens. "ja" nudges it towards Japanese recognition. */
    val language: String = "ja",
    val region: String = "US",
    val timeZone: String = "America/New_York",
    /** Upstream omits platform/surface entirely; keep the default unless experimenting. */
    val includePlatformFields: Boolean = false,
    val maxAttempts: Int = 2,
)

/**
 * Google Lens OCR over the Chromium endpoint.
 *
 * No Google account, no API key of your own: it uses the same public web API and the API key that
 * ship inside Chrome. That also means it is not an officially supported contract — it can change
 * or be blocked at any time, and heavy use will hit rate limits.
 */
class LensClient(
    private val endpoint: String = LensProto.ENDPOINT,
    private val apiKey: String = LensProto.API_KEY,
    private val userAgent: String = DEFAULT_USER_AGENT,
    callTimeoutMillis: Long = 20_000,
    connectTimeoutMillis: Long = 10_000,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AutoCloseable {

    private val memoryCookieJar = object : CookieJar {
        private val store = LinkedHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val merged = LinkedHashMap<String, Cookie>()
            store[url.host]?.forEach { merged[it.name] = it }
            cookies.forEach { merged[it.name] = it }
            store[url.host] = merged.values.toList()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = clock()
            return store[url.host].orEmpty().filter { it.expiresAt > now }
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(memoryCookieJar)
        .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
        .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        // Do NOT set Accept-Encoding by hand: OkHttp transparently gunzips only when it added the
        // header itself.
        .build()

    private var requestCounter = 0L

    fun recognize(
        image: EncodedImage,
        options: LensOptions = LensOptions(),
        furigana: FuriganaMode = FuriganaMode.REMOVE,
    ): LensResult {
        require(image.width > 0 && image.height > 0) { "image dimensions must be positive" }
        require(image.bytes.isNotEmpty()) { "image payload is empty" }

        val started = clock()
        var lastError: Exception? = null

        repeat(options.maxAttempts.coerceAtLeast(1)) { attempt ->
            try {
                val payload = LensProto.buildRequest(
                    image = image,
                    uuid = nextUuid(),
                    language = options.language,
                    region = options.region,
                    timeZone = options.timeZone,
                    includePlatformFields = options.includePlatformFields,
                )

                val request = Request.Builder()
                    .url(endpoint)
                    .header("Content-Type", PROTO_MEDIA_TYPE.toString())
                    .header("X-Goog-Api-Key", apiKey)
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "${options.language},en;q=0.9")
                    .post(payload.toRequestBody(PROTO_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                        throw LensException(
                            "Lens request failed: HTTP ${response.code}${if (body.isBlank()) "" else " - ${body.take(200)}"}",
                            httpStatus = response.code,
                            retryAfterSeconds = parseRetryAfter(response),
                        )
                    }
                    val bytes = response.body?.bytes()
                        ?: throw LensException("Lens returned an empty body", response.code)

                    val parsed = LensProto.parseResponse(bytes, image.width, image.height)
                    val built = LensResultBuilder.build(parsed, image.width, image.height, furigana)
                    return LensResult(
                        language = built.language,
                        segments = built.segments,
                        blocks = built.blocks,
                        elapsedMs = clock() - started,
                    )
                }
            } catch (e: IOException) {
                lastError = LensException("Network error talking to Lens: ${e.message}", cause = e)
            } catch (e: LensException) {
                lastError = e
                // 4xx (other than 429) will not get better by retrying.
                if (e.httpStatus in 400..499 && e.httpStatus != 429) throw e
            }
            if (attempt < options.maxAttempts - 1) Thread.sleep(350L * (attempt + 1))
        }

        throw lastError ?: LensException("Lens request failed")
    }

    suspend fun recognizeSuspend(
        image: EncodedImage,
        options: LensOptions = LensOptions(),
        furigana: FuriganaMode = FuriganaMode.REMOVE,
    ): LensResult = withContext(Dispatchers.IO) { recognize(image, options, furigana) }

    private fun nextUuid(): Long {
        // Mirrors `String(Date.now()) + String(random)`: a 19-digit-ish number, still < 2^63.
        requestCounter = (requestCounter + 1) and 0xFFFF
        val millis = clock()
        val suffix = (requestCounter * 7919L) % 1_000_000L
        return millis * 1_000_000L + suffix
    }

    /**
     * Reads `Retry-After` in either of its two legal forms: delay-seconds or an HTTP-date.
     *
     * Returns null when the header is absent — which is the normal case for a successful call.
     */
    private fun parseRetryAfter(response: Response): Int? {
        val raw = response.header("Retry-After")?.trim().orEmpty()
        raw.toIntOrNull()?.let { return it.coerceIn(0, MAX_RETRY_AFTER_SECONDS) }

        val date = runCatching { response.headers.getDate("Retry-After") }.getOrNull() ?: return null
        val seconds = (date.time - clock()) / 1000L
        return seconds.coerceIn(0L, MAX_RETRY_AFTER_SECONDS.toLong()).toInt()
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        val PROTO_MEDIA_TYPE = "application/x-protobuf".toMediaType()

        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /** A day is already absurd for a retry hint; this is just to keep a bad value sane. */
        private const val MAX_RETRY_AFTER_SECONDS = 86_400
    }
}
