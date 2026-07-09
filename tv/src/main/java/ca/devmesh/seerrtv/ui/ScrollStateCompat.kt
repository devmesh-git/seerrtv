package ca.devmesh.seerrtv.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy

/**
 * Drop-in replacement for [ScrollState.animateScrollTo] that cannot crash with
 * `ClassCastException: java.lang.Float cannot be cast to kotlin.Unit`.
 *
 * Foundation's `animateScrollTo` is declared to return `Unit` but is compiled as a *tail call*
 * to [animateScrollBy] (which returns `Float`): it has no coroutine state machine of its own and
 * hands the caller's continuation straight to `animateScrollBy` (verified by disassembling
 * foundation 1.11.1 and 1.11.4 — identical bytecode, no `ScrollState$animateScrollTo$1` class).
 * So whenever the scroll actually suspends, the caller is resumed with `animateScrollBy`'s boxed
 * `Float`, not `Unit`. That is legal under the long-standing coroutines codegen convention that
 * callers ignore the resumed value of Unit-returning suspend calls — but Kotlin 2.4.0 emits
 * `checkcast kotlin/Unit` on that value at some resume sites, which crashes
 * (seen in `MediaDetails`' auto-scroll `LaunchedEffect`, non-deterministically across sites).
 *
 * Calling `animateScrollBy` directly sidesteps the mismatch entirely: its declared return type
 * (`Float`) matches the value it actually resumes with, so the compiler can never emit a `Unit`
 * cast. The delta math below is exactly what foundation's `animateScrollTo` does internally.
 *
 * Do not "simplify" call sites back to `animateScrollTo` while the app builds with Kotlin 2.4.x.
 *
 * @return the scroll distance actually consumed, like [animateScrollBy]. Callers may ignore it.
 */
internal suspend fun ScrollState.animateScrollToCompat(target: Int): Float =
    animateScrollBy((target - value).toFloat())
