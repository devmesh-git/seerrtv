import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "ca.devmesh.seerrtv.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Profile generation relies on platform tooling that only exists from API 28.
        // This floor applies to the device that *generates* the profile, not to the app,
        // which still ships minSdk 23.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // :tv has two flavor dimensions and this module has none, so every variant would be
        // ambiguous without a pin. `play` is what Play users actually install; `direct` would
        // hit the GitHub releases API during startup and bake network code into the profile.
        missingDimensionStrategy("distribution", "play")
        missingDimensionStrategy("mode", "app")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":tv"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

baselineProfile {
    // Generate against a running device rather than a Gradle Managed Device. GMD refuses TV
    // system images outright ("TV and Auto devices are presently not supported with Gradle
    // Managed Devices"), and a phone image is not a substitute: SeerrTV declares
    // android.software.leanback as required, so it will not install on one, and even if it
    // did it would lay out for the wrong form factor and record the wrong composition work.
    //
    // Boot a TV emulator first (the Google_TV_1080p AVD), then run:
    //     ./gradlew :tv:generateBaselineProfile
    //
    // If more than one device is attached, pin the right one with ANDROID_SERIAL, e.g.
    //     ANDROID_SERIAL=emulator-5554 ./gradlew :tv:generateBaselineProfile
    // otherwise the test runs on every connected device, phones included.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
