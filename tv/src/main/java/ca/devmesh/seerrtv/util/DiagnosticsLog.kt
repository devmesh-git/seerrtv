package ca.devmesh.seerrtv.util

import android.content.Context
import android.content.SharedPreferences
import ca.devmesh.seerrtv.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One recorded problem, shown in Settings > Diagnostics.
 *
 * [summary] is the one-line list entry; [detail] is the full text shown when the user opens the
 * record, and is what we want them to screenshot and post for support.
 */
@Serializable
data class DiagnosticEntry(
    val timestamp: Long,
    val category: String,
    val summary: String,
    val detail: String,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val repeatCount: Int = 1
)

/**
 * Persisted, capped log of problems the app hit at runtime.
 *
 * This exists because several failures are deliberately non-fatal — most notably image requests,
 * where OkHttp rethrows out of its dispatcher thread and would otherwise kill the process. Those
 * no longer crash, which means they also never reach Play Console (it only reports fatal crashes).
 * Recording them here keeps the signal reachable: the user opens Settings > Diagnostics, drills
 * into a record, and screenshots it for support.
 *
 * Writes arrive from background threads (the OkHttp dispatcher's uncaught-exception handler, API
 * coroutines), so all access is synchronized. Capped at [MAX_ENTRIES], newest first. Repeats of
 * the same problem collapse into a single entry with a count rather than flooding the log.
 */
object DiagnosticsLog {

    private const val PREFS_NAME = "seerrtv_diagnostics"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 50
    private const val MAX_DETAIL_CHARS = 2000
    private const val MAX_SUMMARY_CHARS = 200

    /** Repeats of an identical problem within this window collapse into one entry. */
    private const val DEDUPE_WINDOW_MS = 60_000L

    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }

    /** Records a problem. Safe to call from any thread; never throws. */
    fun record(context: Context, category: String, summary: String, detail: String) {
        try {
            synchronized(lock) {
                val prefs = prefs(context)
                val existing = read(prefs)
                val now = System.currentTimeMillis()
                val entry = DiagnosticEntry(
                    timestamp = now,
                    category = category,
                    summary = summary.take(MAX_SUMMARY_CHARS),
                    detail = detail.take(MAX_DETAIL_CHARS)
                )

                // Collapse a repeat of the newest entry instead of appending a near-duplicate.
                val newest = existing.firstOrNull()
                val updated = if (
                    newest != null &&
                    newest.category == entry.category &&
                    newest.summary == entry.summary &&
                    now - newest.timestamp < DEDUPE_WINDOW_MS
                ) {
                    buildList {
                        add(newest.copy(timestamp = now, repeatCount = newest.repeatCount + 1))
                        addAll(existing.drop(1))
                    }
                } else {
                    buildList {
                        add(entry)
                        addAll(existing)
                    }
                }

                prefs.edit()
                    .putString(KEY_ENTRIES, json.encodeToString(updated.take(MAX_ENTRIES)))
                    .apply()
            }
        } catch (_: Throwable) {
            // Diagnostics must never be the reason something fails — this is called from an
            // uncaught-exception handler, where throwing again would be fatal.
        }
    }

    /** Convenience for exceptions: summary from the type/message, detail from the stack trace. */
    fun recordThrowable(context: Context, category: String, context_: String, throwable: Throwable) {
        val summary = "${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}"
        val detail = buildString {
            appendLine(context_)
            appendLine()
            appendLine(throwable.toString())
            throwable.stackTrace.take(12).forEach { appendLine("    at $it") }
            throwable.cause?.let { cause ->
                appendLine()
                appendLine("Caused by: $cause")
                cause.stackTrace.take(6).forEach { appendLine("    at $it") }
            }
        }
        record(context, category, summary, detail)
    }

    /** Recorded problems, newest first. */
    fun entries(context: Context): List<DiagnosticEntry> = synchronized(lock) {
        read(prefs(context))
    }

    fun clear(context: Context) {
        synchronized(lock) {
            prefs(context).edit().remove(KEY_ENTRIES).apply()
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun read(prefs: SharedPreferences): List<DiagnosticEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<DiagnosticEntry>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
