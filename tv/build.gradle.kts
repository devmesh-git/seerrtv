import java.io.File
import java.util.Properties

// Single source for app version; used in defaultConfig and for direct-release APK naming
val appVersionName = "0.28.01"
val appVersionCode = 124

plugins {
    // https://developer.android.com/jetpack/androidx/releases/hilt
    // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html#jetpack-compose-and-compose-multiplatform-release-cycles
    // https://github.com/google/ksp/releases
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ca.devmesh.seerrtv"
    compileSdk = 36
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
        minSdk = 25
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "VERSION_NAME", "\"${defaultConfig.versionName}\"")
        buildConfigField("Boolean", "DEBUG", "true")
        buildConfigField("Boolean", "IS_DIRECT_FLAVOR", "false")
        buildConfigField("Boolean", "IS_LAUNCHER_BUILD", "false")
        buildConfigField("String", "BROWSER_CONFIG_BASE_URL", "\"${browserConfigProperties.getProperty("browser.config.base.url", "https://seerrtv.devmesh.ca")}\"")
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

dependencies {
    // Core Android Dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)

    // Add desugaring library
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Compose Dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Material Design & TV Components
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
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

    // Networking
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Image Loading
    implementation(libs.coil3.coil.compose)
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
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${libs.versions.kotlinxSerializationJson.get()}")
    testImplementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
}

// Rename direct release APKs to SeerrTV-vX.Y.Z.apk and SeerrTV-vX.Y.Z-launcher.apk (runs after assemble)
// Registered in afterEvaluate so variant tasks exist
project.afterEvaluate {
    fun registerRenameDirectApkTask(flavorSuffix: String, isLauncher: Boolean) {
        val variantDir = "direct$flavorSuffix"
        val assembleTaskName = "assembleDirect${flavorSuffix}Release"
        val taskName = "renameDirect${flavorSuffix}ReleaseApk"
        tasks.register(taskName) {
            group = "build"
            description = "Renames direct $flavorSuffix release APK to SeerrTV-v${appVersionName}${if (isLauncher) "-launcher" else ""}.apk"
            dependsOn(assembleTaskName)
            val launcherSuffix = if (isLauncher) "-launcher" else ""
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
    // So Gradle's task dependency validation is satisfied: printBuildOutputs reads APK dirs written by rename tasks
    tasks.named("printBuildOutputs") {
        mustRunAfter(tasks.named("renameDirectAppReleaseApk"), tasks.named("renameDirectLauncherReleaseApk"))
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
    description = "Assembles both direct app and launcher release APKs (SeerrTV-vX.Y.Z.apk, SeerrTV-vX.Y.Z-launcher.apk)."
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
    inputs.dir(apkRoot)

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