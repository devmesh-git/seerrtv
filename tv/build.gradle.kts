import java.io.File
import java.util.Properties
// Imported rather than fully qualified at the use site: inside a Gradle build script `java`
// resolves to the Java plugin extension, so `java.util.zip.ZipFile` parses as property access.
import java.util.zip.ZipFile

// Single source for app version; used in defaultConfig and for direct-release APK naming
val appVersionName = "0.31.0"
val appVersionCode = 140

plugins {
    // https://developer.android.com/jetpack/androidx/releases/hilt
    // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html#jetpack-compose-and-compose-multiplatform-release-cycles
    // https://github.com/google/ksp/releases
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.detekt) apply false
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Static analysis for dead/unused code (config/detekt/detekt.yml). detekt 1.23.8 (the latest
// release) calls a Gradle API deprecated on Gradle 9 at plugin-apply time, which would warn on
// every build. Applying it lazily — only when a detekt task is actually requested — keeps normal
// builds warning-free. Run with: ./gradlew detekt
val detektRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':').startsWith("detekt", ignoreCase = true)
}
if (detektRequested) {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom("$rootDir/config/detekt/detekt.yml")
    }
}

android {
    namespace = "ca.devmesh.seerrtv"
    compileSdk = 37
    buildFeatures {
        buildConfig = true
    }

    // Load signing properties
    val signingPropertiesFile = file("signing.properties")
    val signingProperties = if (signingPropertiesFile.exists()) {
        Properties().apply {
            load(signingPropertiesFile.inputStream())
        }
    } else {
        Properties()
    }

    // Load browser config properties
    val browserConfigPropertiesFile = file("browser-config.properties")
    val browserConfigProperties = if (browserConfigPropertiesFile.exists()) {
        Properties().apply {
            load(browserConfigPropertiesFile.inputStream())
        }
    } else {
        // Fallback to template if browser-config.properties doesn't exist
        val templateFile = file("browser-config.properties.template")
        if (templateFile.exists()) {
            Properties().apply {
                load(templateFile.inputStream())
            }
        } else {
            Properties()
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(signingProperties.getProperty("storeFile", "../localSigningKey.jks"))
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: signingProperties.getProperty("storePassword", "your_keystore_password")
            keyAlias = System.getenv("KEY_ALIAS") ?: signingProperties.getProperty("keyAlias", "your_key_alias")
            keyPassword = System.getenv("KEY_PASSWORD") ?: signingProperties.getProperty("keyPassword", "your_key_password")
        }
    }

    defaultConfig {
        applicationId = "ca.devmesh.seerrtv"
        minSdk = 23
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "VERSION_NAME", "\"${defaultConfig.versionName}\"")
        buildConfigField("Boolean", "DEBUG", "true")
        buildConfigField("Boolean", "IS_DIRECT_FLAVOR", "false")
        buildConfigField("Boolean", "IS_LAUNCHER_BUILD", "false")
        buildConfigField("String", "BROWSER_CONFIG_BASE_URL", "\"${browserConfigProperties.getProperty("browser.config.base.url", "https://seerrtv.devmesh.ca")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    buildTypes {
        release {
            // Play flags apps whose DEX is under-optimised once they exceed 10 MB uncompressed;
            // SeerrTV ships 34.4 MB, and with R8 off the obfuscation score sat at 1% against a
            // 25% threshold. See proguard-rules.pro for the keep rules this needs.
            isMinifyEnabled = true
            // Resource shrinking rides on code shrinking: R8 has to have determined which R.*
            // references survive before unused resources can be identified. Safe here because
            // nothing looks resources up by name — there is no getIdentifier call in the app —
            // so every reference is statically visible. Note this does not conflict with
            // `bundle { language { enableSplit = false } }` below: that controls which locales
            // are *packaged per device*, while shrinking only removes resources nothing refers
            // to at all, and every translated string is reachable through R.string.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }

        // The baseline profile plugin needs these two build types and will create them itself
        // with initWith(release) if they are absent — which copies the release signing config.
        // They exist only to be installed on a throwaway device while generating a profile and
        // are never shipped, so requiring the release keystore to build them would block
        // profile generation on any machine without it. Declaring them here instead of letting
        // the plugin do it is what makes the debug key stick: the plugin's initWith runs after
        // the android block is evaluated, so anything set afterwards (configureEach,
        // afterEvaluate) is either overwritten or lands after AGP has already snapshotted the
        // signing config into the variant.
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            // initWith(release) copies isShrinkResources = true, and AGP fails the build if
            // resource shrinking is on while code shrinking is off. This variant must stay
            // unminified so the generated profile carries real names, so turn it back off.
            isShrinkResources = false
        }
        create("benchmarkRelease") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // One body of contract tests, compiled into both test source sets.
    //
    // src/test runs them on the JVM against unminified classes — fast, every build. src/androidTest
    // runs the identical assertions on a device, and because testBuildType is "release" below, that
    // happens inside the R8-processed APK. Only the second can tell you a serializer survived
    // obfuscation, which is the whole reason this split exists.
    sourceSets {
        // kotlin.srcDir, not java.srcDir: under AGP 9's built-in Kotlin support a directory
        // registered only as a Java source dir contributes no .kt files, so the tests compiled
        // into nothing and silently did not run.
        getByName("test") {
            kotlin.srcDir("src/sharedTest/java")
            resources.srcDir("src/sharedTest/resources")
        }
        getByName("androidTest") {
            kotlin.srcDir("src/sharedTest/java")
            // Instrumented tests read fixtures off the classpath the same way, so the resources
            // have to be packaged into the test APK too, not just the JVM test runtime.
            resources.srcDir("src/sharedTest/resources")
        }
    }

    // NOTE: testBuildType is deliberately left at its default ("debug").
    //
    // Setting it to "release" so ApiContractTest would execute inside the minified APK does very
    // nearly work — the runner starts, finds all five tests and runs them — but every test then
    // fails on a core-library-desugaring collision: both the app and the test APK receive their
    // own L8-generated copy of the j$-namespaced JDK backport, each minimised against its own
    // call sites, and they disagree ("No direct method <init>(I)V in class
    // j$.util.concurrent.ConcurrentHashMap"). L8 runs after R8, so no keep rule reaches it, and
    // core library desugaring cannot simply be turned off — minSdk is 23 and the app relies on
    // it. Getting that far also required keeping androidx.tracing.Trace, kotlin.LazyKt and the
    // seerrApiJson accessor in the *shipped* app purely so the test APK could resolve them,
    // which taxes the obfuscation score 0.31.0 exists to raise.
    //
    // The R8 verification these tests were meant to provide is better served by asserting over
    // the mapping file and DEX after R8 runs — no emulator, no keep rules, no shipped cost.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // android.util.Log is a stub in JVM unit tests and throws "not mocked" when called.
            // Returning defaults instead lets tests exercise code paths that log — notably the
            // profile-decode failure path, whose whole job is to log and degrade safely.
            isReturnDefaultValues = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    bundle {
        // enableSplit is @Incubating but is AGP's only API for this; no stable alternative.
        @Suppress("UnstableApiUsage")
        language {
            // Ship every translation to every device.
            //
            // By default Play splits resources by language and installs only the split matching
            // the device's system locale. The app has its own App Language picker, so a device
            // set to English would switch to Spanish and find no values-es/ on disk — the
            // setting stuck but every string fell back to the base (English) resources.
            //
            // The per-app-language API (AppCompatDelegate.setApplicationLocales) makes Play
            // fetch the needed split on demand, but only on API 33+. SeerrTV supports Android
            // TV devices well below that (NVIDIA Shield is API 30, Chromecast with Google TV
            // is API 31), so disabling the split is the only fix that covers them. Costs a few
            // hundred KB of strings in a base APK that is already ~57 MB.
            enableSplit = false
        }
    }

    flavorDimensions += "distribution"
    flavorDimensions += "mode"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_DIRECT_FLAVOR", "false")
        }
        create("direct") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_DIRECT_FLAVOR", "true")
        }

        create("app") {
            dimension = "mode"
            // Standard TV app behavior (tile on system home)
            applicationId = "ca.devmesh.seerrtv"
            buildConfigField("Boolean", "IS_LAUNCHER_BUILD", "false")
        }
        create("launcher") {
            dimension = "mode"
            // Launcher build: can coexist with the standard app
            applicationId = "ca.devmesh.seerrtv.launcher"
            buildConfigField("Boolean", "IS_LAUNCHER_BUILD", "true")
        }
    }

    ndkVersion = "27.0.12077973"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

baselineProfile {
    // All four variants (play/direct x app/launcher) share the same startup code path, so one
    // profile checked into src/main covers them all. Without this the plugin would emit a
    // separate copy per variant.
    mergeIntoMain = true

    // Keep generation an explicit, manual step (./gradlew generateBaselineProfile). Generating
    // during every release build would require a connected TV device or a managed-device boot
    // on any machine cutting a release, including CI.
    automaticGenerationDuringBuild = false

    // Check the generated profile into git so release builds pick it up without regenerating.
    saveInSrc = true
}

dependencies {
    // Core Android Dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Applies src/main/generated/baselineProfiles at first run on API 28-30; API 31+ reads the
    // profile straight out of the APK.
    implementation(libs.androidx.profileinstaller)

    // Supplies the generated profile to this module (see :baselineprofile).
    baselineProfile(project(":baselineprofile"))

    // Add desugaring library
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Compose Dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // ui-test-junit4 takes its version from the Compose BOM, which was only applied to the
    // implementation configuration — so this dependency had never actually resolved. It went
    // unnoticed because the module had no androidTest source set to build.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // Needed by the shared contract tests when they run instrumented.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // androidx.test references @CanIgnoreReturnValue / @MustBeClosed, which nothing declares
    // transitively, so R8 on the test APK fails with "Missing classes detected". Same shape as
    // the Hilt case above; compile-time only, CLASS retention.
    androidTestImplementation(libs.errorprone.annotations)

    // Material Design & TV Components
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.foundation)

    // Navigation & Lifecycle
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    // Hilt/Dagger 2.60 no longer pulls this transitively, but generated code needs it at compile time.
    compileOnly(libs.errorprone.annotations)

    // Networking
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)

    // Image Loading
    // Coil 3's coil-compose is a Kotlin-Multiplatform artifact whose Android variant can resolve to
    // JetBrains Compose Multiplatform (org.jetbrains.compose.*) instead of redirecting to AndroidX.
    // Those artifacts ship duplicate copies of the androidx.compose.* classes; having both them and
    // the real AndroidX Compose artifacts on the classpath causes runtime ClassCastExceptions (e.g.
    // ScrollState.animateScrollTo in MediaDetails). Exclude them so only the BOM-managed AndroidX
    // Compose artifacts remain — they provide the same classes Coil compiles against.
    implementation(libs.coil3.coil.compose) {
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.animation")
        exclude(group = "org.jetbrains.compose.ui")
    }
    implementation(libs.coil.network.okhttp)

    // Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Utilities
    implementation(libs.core)

    // YouTube embed player (IFrame API wrapper; WebView-based)
    implementation(libs.androidyoutubeplayer.core)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
}

// Rename direct release APKs to SeerrTV-vX.Y.Z.apk and SeerrTV-vX.Y.Z.launcher.apk (runs after assemble).
// ".launcher" before ".apk" sorts after the main APK name on GitHub so legacy "first .apk" updaters get the main build.
// Registered in afterEvaluate so variant tasks exist
project.afterEvaluate {
    fun registerRenameDirectApkTask(flavorSuffix: String, isLauncher: Boolean) {
        val variantDir = "direct$flavorSuffix"
        val assembleTaskName = "assembleDirect${flavorSuffix}Release"
        val taskName = "renameDirect${flavorSuffix}ReleaseApk"
        tasks.register(taskName) {
            group = "build"
            description = "Renames direct $flavorSuffix release APK to SeerrTV-v${appVersionName}${if (isLauncher) ".launcher" else ""}.apk"
            dependsOn(assembleTaskName)
            val launcherSuffix = if (isLauncher) ".launcher" else ""
            val targetName = "SeerrTV-v${appVersionName}${launcherSuffix}.apk"
            val apkDir = layout.buildDirectory.dir("outputs/apk/$variantDir/release")
            inputs.files(apkDir.map { it.asFileTree.matching { include("*.apk") } })
            outputs.file(apkDir.map { File(it.asFile, targetName) })
            doLast {
                val dir = apkDir.get().asFile
                val apks = dir.listFiles { f -> f.isFile && f.extension.equals("apk", ignoreCase = true) }.orEmpty()
                val apk = apks.singleOrNull()
                    ?: throw GradleException("Expected exactly one APK in $dir, found: ${apks.map { it.name }}")
                val dest = File(dir, targetName)
                if (apk != dest) {
                    apk.renameTo(dest)
                    logger.lifecycle("Renamed ${apk.name} -> $targetName")
                }
            }
        }
        tasks.named(assembleTaskName) {
            finalizedBy(taskName)
        }
    }
    registerRenameDirectApkTask("App", false)
    registerRenameDirectApkTask("Launcher", true)
    // printBuildOutputs reports whatever APKs and AABs are on disk, so it has to run after
    // everything that can write one — AGP's per-variant package* tasks as well as the rename*
    // tasks above. Naming them individually is what went wrong before: the list only covered
    // the two direct renames, so any other producer in the same build either failed validation
    // or raced. Matching by name keeps new variants covered automatically.
    tasks.named("printBuildOutputs") {
        mustRunAfter(tasks.matching { it.name.startsWith("package") || it.name.startsWith("rename") })
    }
}

// Generate BuildConfig for all debug variants so the IDE can resolve it after a clean,
// regardless of which variant is active in the Build Variants panel.
// Runs automatically before preBuild so it's included in every build and IDE sync.
tasks.register("generateAllDebugBuildConfigs") {
    group = "build"
    description = "Generates BuildConfig for all debug variants (keeps IDE references valid after clean)."
    dependsOn(
        "generateDirectAppDebugBuildConfig",
        "generatePlayAppDebugBuildConfig",
        "generateDirectLauncherDebugBuildConfig",
        "generatePlayLauncherDebugBuildConfig",
    )
}

// Hook into the IDE's Gradle sync model preparation so BuildConfig is always present after a sync.
tasks.matching { it.name == "prepareKotlinBuildScriptModel" }.configureEach {
    dependsOn("generateAllDebugBuildConfigs")
}

// Single entry points for direct: build both app and launcher APKs
tasks.register("assembleDirectDebug") {
    group = "build"
    description = "Assembles both direct app and launcher debug APKs."
    dependsOn("assembleDirectAppDebug", "assembleDirectLauncherDebug")
}
tasks.register("assembleDirectRelease") {
    group = "build"
    description = "Assembles both direct app and launcher release APKs (SeerrTV-vX.Y.Z.apk, SeerrTV-vX.Y.Z.launcher.apk)."
    dependsOn("assembleDirectAppRelease", "assembleDirectLauncherRelease")
}

// Single entry points for Play builds (disambiguate app vs launcher)
tasks.register("bundlePlayDebug") {
    group = "build"
    description = "Builds the Play Store debug AAB (main app)."
    dependsOn("bundlePlayAppDebug")
}
tasks.register("bundlePlayRelease") {
    group = "build"
    description = "Builds the Play Store release AAB (main app) for upload to Play Console."
    dependsOn("bundlePlayAppRelease")
}

// Simple task to print APK and AAB output locations (configuration-cache safe: uses only serializable inputs)
tasks.register("printBuildOutputs") {
    group = "build"
    description = "Prints APK and AAB output locations for the tv module."

    val apkRoot = objects.directoryProperty()
    val bundleRoot = objects.directoryProperty()
    apkRoot.set(layout.buildDirectory.dir("outputs/apk"))
    bundleRoot.set(layout.buildDirectory.dir("outputs/bundle"))
    // Deliberately no inputs.dir(apkRoot). This task has no outputs, so it is never considered
    // up to date and always re-runs, walking the directory itself in doLast — the input
    // declaration bought no up-to-date checking. What it did buy was an obligation: Gradle then
    // required an ordering rule against every task that writes into outputs/apk, and only the
    // two direct rename tasks were covered. Asking for a Play variant and a direct variant in
    // one invocation — which is exactly what cutting a release looks like — failed validation
    // on packagePlayAppRelease. See the ordering rule near registerRenameDirectApkTask.

    doLast {
        val apkRootDir = apkRoot.get().asFile
        val bundleRootDir = bundleRoot.get().asFile

        fun listFilesRecursively(root: File, extension: String): List<File> {
            if (!root.exists()) return emptyList()
            return root.walkTopDown().filter { it.isFile && it.extension == extension }.toList()
        }

        val apks = listFilesRecursively(apkRootDir, "apk")
        val aabs = listFilesRecursively(bundleRootDir, "aab")

        if (apks.isEmpty() && aabs.isEmpty()) {
            println("No APK or AAB outputs found under outputs/apk or outputs/bundle")
            return@doLast
        }

        println("\n" + "=".repeat(70))
        println("BUILD OUTPUTS")
        println("=".repeat(70))

        if (apks.isNotEmpty()) {
            println("\nAPK files:")
            apks.forEach { println("   ${it.absolutePath}") }
        }

        if (aabs.isNotEmpty()) {
            println("\nAAB files:")
            aabs.forEach { println("   ${it.absolutePath}") }
        }

        println("\n" + "=".repeat(70) + "\n")
    }
}

// Print APK/AAB paths after main build entry points only (assembleDebug builds all variants and is not a primary target)
listOf(
    "assembleDirectDebug",
    "assembleDirectRelease",
    "bundlePlayDebug",
    "bundlePlayRelease"
).forEach { taskName ->
    tasks.matching { it.name == taskName }.configureEach {
        finalizedBy("printBuildOutputs")
    }
}
// ---------------------------------------------------------------------------
// Post-R8 verification
// ---------------------------------------------------------------------------
// Unit tests run on unminified classes, so they cannot tell you whether R8 kept what the app
// needs at runtime. Running them inside the minified APK does not work here (see the note at
// testBuildType), so instead this asserts the properties directly over R8's own output.
//
// Everything it checks is a failure that is silent at build time and only shows up as a crash,
// a spinner or an unreadable stack trace in the field:
//
//  1. Generated kotlinx.serialization serializers. Reached only reflectively, so R8 cannot see
//     the link; if a keep rule regresses they vanish and every API response fails to decode.
//  2. The YouTube player's JavaScript bridge. res/raw/ayp_youtube_player.html calls
//     YouTubePlayerBridge.sendReady() and siblings by name; rename them and the trailer player
//     spins forever with no crash and nothing in logcat.
//  3. SourceFile/LineNumberTable, without which Play vitals ANR traces lose the line numbers
//     that made the 0.29.0 ANRs diagnosable at all.
//  4. The DEX optimisation scores Play measures, so a future broad keep rule cannot quietly
//     push the app back under the threshold that prompted enabling R8.
//
// Scoped to playAppRelease: that is the variant uploaded to Play, and all four release variants
// share one proguard configuration.
tasks.register("verifyR8Output") {
    group = "verification"
    description = "Asserts the R8-processed release output kept the serializers, JS bridge and line numbers the app needs."

    // Explicit, because this reads other tasks' outputs — the same trap that broke
    // printBuildOutputs. Note it is mergeComposeMapping, not minifyWithR8, that writes the final
    // mapping.txt: R8 produces it and the Compose mapping merge then rewrites it in place, so
    // depending on the R8 task alone still races.
    dependsOn("mergePlayAppReleaseComposeMapping")
    // The bundle is read opportunistically for r8.json. If it is being built in the same
    // invocation, read it after it is written rather than a stale copy from a previous run.
    mustRunAfter("bundlePlayAppRelease", "packagePlayAppRelease")

    val mapping = layout.buildDirectory.file("outputs/mapping/playAppRelease/mapping.txt")
    val bundle = layout.buildDirectory.file("outputs/bundle/playAppRelease/tv-play-app-release.aab")
    val sourceRoot = layout.projectDirectory.dir("src/main/java/ca/devmesh/seerrtv").asFile
    inputs.file(mapping)

    // Cheap, and a stale pass here is worse than re-running it.
    outputs.upToDateWhen { false }

    doLast {
        val mappingText = mapping.get().asFile.readText()
        val failures = mutableListOf<String>()

        // --- 1. every generated serializer survived --------------------------------------
        // Derived from source rather than hard-coded, so a new @Serializable model is covered
        // the moment it is written.
        //
        // Only plain classes are matched, because only they get a generated $$serializer:
        //  - @Serializable(with = …) delegates to a hand-written serializer (SearchResult), and
        //    is excluded by requiring the annotation line to be exactly "@Serializable".
        //  - sealed classes and interfaces get a SealedClassSerializer, so "sealed" is absent
        //    from the modifiers below and they do not match.
        //  - objects get an ObjectSerializer built at runtime — SortOption's ten subclasses are
        //    all @Serializable objects, and treating them as missing was a false positive.
        val declPattern =
            Regex("""^\s*(?:public\s+|internal\s+|private\s+)?(?:data\s+|value\s+)?class\s+(\w+)""")
        val expected = sortedSetOf<String>()
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { i, line ->
                if (line.trim() != "@Serializable") return@forEachIndexed
                for (j in i + 1 until minOf(i + 4, lines.size)) {
                    val name = declPattern.find(lines[j])?.groupValues?.get(1)
                    if (name != null) {
                        expected += name
                        break
                    }
                }
            }
        }
        val missing = expected.filterNot { mappingText.contains("$it\$\$serializer ->") }
        if (missing.isNotEmpty()) {
            failures += "Generated serializers absent from the R8 output for: " +
                missing.joinToString() +
                ". kotlinx.serialization reaches these reflectively, so decoding will fail at " +
                "runtime. Check the kotlinx.serialization keep rules in proguard-rules.pro."
        }

        // --- 2. the YouTube JS bridge kept its method names -------------------------------
        val bridgeMethods = listOf(
            "sendReady", "sendStateChange", "sendError", "sendApiChange",
            "sendPlaybackQualityChange", "sendPlaybackRateChange", "sendVideoCurrentTime",
            "sendVideoDuration", "sendVideoId", "sendVideoLoadedFraction",
            "sendYouTubeIFrameAPIReady"
        )
        val renamed = bridgeMethods.filterNot { mappingText.contains("-> $it") }
        if (renamed.isNotEmpty()) {
            failures += "YouTube bridge methods were renamed or removed: " +
                renamed.joinToString() +
                ". The player's HTML calls these by name, so trailers will hang on a spinner " +
                "with no crash. Check the @android.webkit.JavascriptInterface keep rule."
        }

        // --- 3 and 4. attributes and scores, from the metadata Play reads ------------------
        val aab = bundle.get().asFile
        if (!aab.exists()) {
            logger.lifecycle(
                "verifyR8Output: no bundle at ${aab.name}, skipping the attribute and score " +
                    "checks. Run :tv:bundlePlayAppRelease to include them."
            )
        } else {
            val r8Json: String? = ZipFile(aab).use { zf ->
                val entry = zf.getEntry("BUNDLE-METADATA/com.android.tools/r8.json")
                if (entry == null) null
                else zf.getInputStream(entry).bufferedReader().use { r -> r.readText() }
            }
            if (r8Json == null) {
                failures += "The bundle contains no BUNDLE-METADATA/com.android.tools/r8.json, " +
                    "which is the file Play reads to score DEX optimisation. R8 may not have run."
            } else {
                if (!Regex(""""isSourceFileKept"\s*:\s*true""").containsMatchIn(r8Json)) {
                    failures += "SourceFile is not being kept, so Play vitals stack traces will " +
                        "lose their file and line numbers. Check -keepattributes in " +
                        "proguard-rules.pro."
                }
                // Play reports the share that IS optimised; r8.json stores the inverse.
                val threshold = 25.0
                Regex(""""(no\w+Percentage)"\s*:\s*([0-9.]+)""").findAll(r8Json).forEach { m ->
                    val metric = m.groupValues[1]
                    val notOptimised = m.groupValues[2].toDouble()
                    if (notOptimised >= 100.0 - threshold) {
                        failures += "$metric is $notOptimised%, leaving only " +
                            "${"%.1f".format(100 - notOptimised)}% optimised — at or below the " +
                            "$threshold% floor Play flags. A keep rule was probably widened."
                    }
                }
            }
        }

        if (failures.isNotEmpty()) {
            throw GradleException(
                "R8 output verification failed:\n\n" +
                    failures.joinToString("\n\n") { "  - $it" } + "\n"
            )
        }
        logger.lifecycle(
            "verifyR8Output: ${expected.size} generated serializers, " +
                "${bridgeMethods.size} JS bridge methods, line numbers and DEX scores all intact."
        )
    }
}

// Run the verification automatically whenever the Play release bundle is built, since that is
// the artifact whose DEX scores Play reads and the point at which a regression would ship. It is
// deliberately not wired into `check`: it needs a full R8 pass, which is too slow to impose on
// every ordinary build. Run it directly with ./gradlew :tv:verifyR8Output.
tasks.matching { it.name == "bundlePlayRelease" }.configureEach {
    finalizedBy("verifyR8Output")
}
