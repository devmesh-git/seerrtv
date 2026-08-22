package ca.devmesh.seerrtv.data

import android.content.Context
import android.util.Log
import ca.devmesh.seerrtv.util.DiagnosticsLog
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Executor for an OkHttp [okhttp3.Dispatcher] whose threads never take the process down.
 *
 * OkHttp's `AsyncCall.run` delivers `onFailure` to the caller and then **rethrows** any
 * non-IOException, which reaches Android's default handler and kills the app. That is how a
 * single bad URL becomes a crash: e.g. an `IllegalArgumentException` out of
 * `HttpUrl.Builder.host` when `okhttp3.Address` re-canonicalises the host at connection time.
 * By then the call has already failed cleanly and the caller has been told, so the rethrow buys
 * us nothing — swallowing it here costs no diagnostics (the throwable is logged and recorded)
 * and keeps one broken URL from being fatal.
 *
 * Every OkHttp client in the app needs this, not just the image loader: Ktor's OkHttp engine
 * enqueues its calls too, so the API client runs through the same `AsyncCall.run` rethrow.
 *
 * Mirrors OkHttp's own dispatcher executor (unbounded, 60s keep-alive, SynchronousQueue); only
 * the thread factory differs. OkHttp does not shut down an executor it was handed, which is
 * correct here — these clients live for the life of the process.
 *
 * @param threadName initial name for pool threads. OkHttp renames each thread to
 *   `OkHttp <redacted url>` for the duration of a call, so the name reported to the handler
 *   identifies the request that died rather than this value.
 */
internal fun resilientDispatchExecutor(
    context: Context,
    threadName: String,
    diagnosticsCategory: String,
    diagnosticsContext: String
): ExecutorService {
    val appContext = context.applicationContext
    return ThreadPoolExecutor(
        0,
        Int.MAX_VALUE,
        60L,
        TimeUnit.SECONDS,
        SynchronousQueue()
    ) { runnable ->
        Thread(runnable, threadName).apply {
            isDaemon = false
            setUncaughtExceptionHandler { thread, throwable ->
                Log.e("SeerrTV", "Request thread ${thread.name} died; ignoring", throwable)
                // Swallowing this keeps the app alive but also hides it from Play Console,
                // which only sees fatal crashes. Record it so it is still reachable from
                // Settings > Diagnostics.
                DiagnosticsLog.recordThrowable(
                    context = appContext,
                    category = diagnosticsCategory,
                    context_ = "$diagnosticsContext (on ${thread.name})",
                    throwable = throwable
                )
            }
        }
    }
}
