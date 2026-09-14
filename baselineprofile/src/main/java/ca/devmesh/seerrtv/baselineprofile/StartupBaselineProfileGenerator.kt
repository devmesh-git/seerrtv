package ca.devmesh.seerrtv.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods SeerrTV touches while starting up, so they can be compiled
 * ahead of time instead of interpreted on first run.
 *
 * Why this exists: Play vitals captured an ANR whose main thread was Runnable inside
 * `WindowInsetsRulers.<clinit>`, reached from `AndroidComposeView.<init>` during the very first
 * `setContent`, with a concurrent GC running alongside it. That is a cold start grinding through
 * class loading and verification with nothing precompiled. A baseline profile targets exactly
 * that window.
 *
 * Run with:  ./gradlew :tv:generateBaselineProfile
 * The result lands in tv/src/main/generated/baselineProfiles/ and is meant to be committed.
 */
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            // Also emit a startup profile, which the runtime uses to lay out the dex file so
            // startup classes sit together and fault in with fewer reads. That layout matters
            // more on the slow eMMC storage in cheap TV boxes than it does on a phone.
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // startActivityAndWait returns at the first frame, but SeerrTV's startup continues
            // past it: the splash runs profile initialisation and API validation before the
            // first real screen composes. Without this wait the profile would stop at the
            // splash and miss most of what actually runs.
            device.waitForIdle(STARTUP_IDLE_TIMEOUT_MS)

            exerciseDpadNavigation()
        }
    }

    /**
     * Walks focus around with D-pad events instead of looking up Compose nodes.
     *
     * Which screen startup lands on depends on state stored on the device — an unconfigured
     * device shows the config flow, a configured one shows browse, a multi-profile one shows the
     * profile picker — and a node-based interaction would have to know which in advance. D-pad
     * input is meaningful on all of them, so focus traversal and row rendering get recorded
     * either way and the generator cannot fail by hunting for a node that isn't on screen.
     *
     * Only DOWN and RIGHT: neither commits to anything. Pressing centre would open details or a
     * request dialog depending on where focus happens to be, which would make the recorded
     * profile depend on the device's library contents.
     */
    private fun MacrobenchmarkScope.exerciseDpadNavigation() {
        repeat(TRAVERSAL_STEPS) {
            device.pressDPadDown()
            device.waitForIdle(INTERACTION_IDLE_TIMEOUT_MS)
            device.pressDPadRight()
            device.waitForIdle(INTERACTION_IDLE_TIMEOUT_MS)
        }
    }

    private companion object {
        /** The `play`/`app` variant's applicationId; see missingDimensionStrategy in build.gradle.kts. */
        const val TARGET_PACKAGE = "ca.devmesh.seerrtv"

        /** Generous: the splash may be waiting on a Seerr instance over the network. */
        const val STARTUP_IDLE_TIMEOUT_MS = 10_000L
        const val INTERACTION_IDLE_TIMEOUT_MS = 2_000L
        const val TRAVERSAL_STEPS = 4
    }
}
