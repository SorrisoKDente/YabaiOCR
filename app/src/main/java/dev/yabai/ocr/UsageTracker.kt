package dev.yabai.ocr

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local record of how often Google Lens was called.
 *
 * Why only local numbers: the endpoint has no quota API. A successful call answers with Google's
 * generic frontend headers — no `X-RateLimit-*`, no `Remaining`, no reset window — so there is
 * nothing to read a "how much is left" figure from. What we *can* measure honestly is how many
 * requests we sent, and whether Google started refusing them (429/403). The Retry-After delay is
 * surfaced when the server bothers to send one.
 */
class UsageTracker(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Snapshot(
        val todayCount: Int,
        val totalCount: Int,
        /** First recorded request, or 0 when nothing was ever recorded. */
        val firstAtMillis: Long,
        val lastAtMillis: Long,
        val lastOk: Boolean,
        val lastHttpStatus: Int,
        val lastMessage: String?,
        /** Epoch millis until which Google asked us to wait, or 0 when it never did. */
        val rateLimitedUntilMillis: Long,
    ) {
        /** Seconds left on the current rate limit, 0 when there is none. */
        fun rateLimitRemainingSeconds(nowMillis: Long = System.currentTimeMillis()): Long =
            ((rateLimitedUntilMillis - nowMillis) / 1000L).coerceAtLeast(0L)

        val rateLimitActive: Boolean get() = rateLimitRemainingSeconds() > 0

        /** True when the last request was refused for rate-limit reasons. */
        fun isRefusal(): Boolean =
            !lastOk &&
                (lastHttpStatus == HTTP_TOO_MANY_REQUESTS || lastHttpStatus == HTTP_FORBIDDEN)
    }

    /** Records one Lens request and its outcome. Safe to call from any thread. */
    fun recordLensCall(
        ok: Boolean,
        httpStatus: Int = -1,
        retryAfterSeconds: Int? = null,
        message: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val today = dayKey(now)
        val countingToday = prefs.getString(KEY_DAY, null) == today
        val todayCount = if (countingToday) prefs.getInt(KEY_TODAY, 0) else 0
        val first = prefs.getLong(KEY_FIRST_AT, 0L)
        val refused = !ok && (httpStatus == HTTP_TOO_MANY_REQUESTS || httpStatus == HTTP_FORBIDDEN)

        prefs.edit {
            putString(KEY_DAY, today)
            putInt(KEY_TODAY, todayCount + 1)
            putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            putLong(KEY_FIRST_AT, if (first == 0L) now else first)
            putLong(KEY_LAST_AT, now)
            putBoolean(KEY_LAST_OK, ok)
            putInt(KEY_LAST_STATUS, httpStatus)
            putString(KEY_LAST_MESSAGE, message)

            val currentLimit = prefs.getLong(KEY_RATE_LIMITED_UNTIL, 0L)
            val nextLimit = when {
                // A working call means whatever limit was in force has lifted.
                ok -> 0L
                refused && retryAfterSeconds != null -> now + retryAfterSeconds * 1_000L
                refused -> currentLimit
                else -> currentLimit
            }
            putLong(KEY_RATE_LIMITED_UNTIL, nextLimit)
        }
    }

    fun snapshot(): Snapshot {
        val today = dayKey(System.currentTimeMillis())
        val countingToday = prefs.getString(KEY_DAY, null) == today
        return Snapshot(
            todayCount = if (countingToday) prefs.getInt(KEY_TODAY, 0) else 0,
            totalCount = prefs.getInt(KEY_TOTAL, 0),
            firstAtMillis = prefs.getLong(KEY_FIRST_AT, 0L),
            lastAtMillis = prefs.getLong(KEY_LAST_AT, 0L),
            lastOk = prefs.getBoolean(KEY_LAST_OK, true),
            lastHttpStatus = prefs.getInt(KEY_LAST_STATUS, -1),
            lastMessage = prefs.getString(KEY_LAST_MESSAGE, null),
            rateLimitedUntilMillis = prefs.getLong(KEY_RATE_LIMITED_UNTIL, 0L),
        )
    }

    fun reset() = prefs.edit { clear() }

    private fun dayKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    companion object {
        private const val PREFS_NAME = "yabai_ocr_usage"

        private const val KEY_DAY = "day"
        private const val KEY_TODAY = "today"
        private const val KEY_TOTAL = "total"
        private const val KEY_FIRST_AT = "first_at"
        private const val KEY_LAST_AT = "last_at"
        private const val KEY_LAST_OK = "last_ok"
        private const val KEY_LAST_STATUS = "last_status"
        private const val KEY_LAST_MESSAGE = "last_message"
        private const val KEY_RATE_LIMITED_UNTIL = "rate_limited_until"

        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_FORBIDDEN = 403
    }
}
