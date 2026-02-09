import java.io.File
import java.util.Properties

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
        versionCode = 112
        versionName = "0.26.9"
        buildConfigField("String", "VERSION_NAME", "\"${defaultConfig.versionName}\"")
        buildConfigField("Boolean", "DEBUG", "true")
        buildConfigField("Boolean", "IS_DIRECT_FLAVOR", "false")
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
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_DIRECT_FLAVOR", "false")
        }
        create("direct") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_DIRECT_FLAVOR", "true")
        }
    }
    ndkVersion = "27.0.12077973"
    buildToolsVersion =
        "36.1.0"// To set 'playDebug' as the default build variant, use the Build Variants panel in Android Studio.
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

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${libs.versions.kotlinxSerializationJson.get()}")
    testImplementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
}

// Simple task to print APK and AAB output locations
tasks.register("printBuildOutputs") {
    group = "build"
    description = "Prints APK and AAB output locations for the tv module."

    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val apkRoot = File(buildDir, "outputs/apk")
        val bundleRoot = File(buildDir, "outputs/bundle")

        fun listFilesRecursively(root: File, extension: String): List<File> {
            if (!root.exists()) return emptyList()
            return root.walkTopDown().filter { it.isFile && it.extension == extension }.toList()
        }

        val apks = listFilesRecursively(apkRoot, "apk")
        val aabs = listFilesRecursively(bundleRoot, "aab")

        if (apks.isEmpty() && aabs.isEmpty()) {
            println("No APK or AAB outputs found under ${buildDir.absolutePath}")
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

// Automatically print outputs after assembling debug or release flavors
listOf(
    "assembleDebug",
    "assembleDirectRelease",
    "assemblePlayRelease"
).forEach { assembleTaskName ->
    tasks.matching { it.name == assembleTaskName }.configureEach {
        finalizedBy("printBuildOutputs")
    }
}