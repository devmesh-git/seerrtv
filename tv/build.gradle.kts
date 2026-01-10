//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Locale
import java.util.Properties

plugins {
    // https://developer.android.com/jetpack/androidx/releases/hilt
    // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html#jetpack-compose-and-compose-multiplatform-release-cycles
    // https://github.com/google/ksp/releases
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
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
        versionCode = 106
        versionName = "0.26.3"
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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
}

// Add UpdateJson generation task
abstract class WriteUpdateJsonTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val apkUrl: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val versionName: Property<String>

    @TaskAction
    fun writeJson() {
        // Create the JSON structure
        val json = mapOf(
            "latestVersionCode" to versionCode.get(),
            "latestVersionName" to versionName.get(),
            "apkUrl" to apkUrl.get(),
        )

        // Ensure output directory exists
        outputDir.get().asFile.mkdirs()

        // Write the JSON to a file
        val jsonFile = outputDir.get().asFile.resolve("update.json")
        jsonFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)))
        
        logger.lifecycle("Generated update.json at ${jsonFile.absolutePath}")
    }
}

// Register the task
tasks.register<WriteUpdateJsonTask>("generateUpdateJson") {
    group = "distribution"
    description = "Generates update.json for direct flavor"
    
    val versionName = android.defaultConfig.versionName
    val versionCode = android.defaultConfig.versionCode

    outputDir.set(layout.buildDirectory.dir("outputs/updateJson"))
    apkUrl.set("https://release.devmesh.ca/u/seerrTV-v${versionName}.apk")
    this.versionCode.set(versionCode)
    this.versionName.set(versionName)
}

// Hook the task into the build process to print outputs
android.applicationVariants.all {
    val variantName = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val assembleTaskName = "assemble$variantName"
    val bundleTaskName = "bundle$variantName"
    val flavor = flavorName
    val buildType = buildType.name
    val buildDirPath = layout.buildDirectory.get().asFile.absolutePath
    
    // For bundle tasks, print AAB location
    if (buildType == "release") {
        tasks.named(bundleTaskName).configure {
            val variant = "${flavor}${buildType.replaceFirstChar { it.uppercase() }}"
            val aabPath = "${buildDirPath}/outputs/bundle/${variant}/tv-${flavor}-${buildType}.aab"
            val flavorDisplay = flavor.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            doLast {
                val aabFile = File(aabPath)
                if (aabFile.exists()) {
                    println("\n" + "=".repeat(70))
                    println("BUILD OUTPUTS")
                    println("=".repeat(70))
                    println("\n📦 $flavorDisplay Release AAB:")
                    println("   ${aabFile.absolutePath}")
                    println("\n" + "=".repeat(70) + "\n")
                }
            }
        }
    }
    
    // For direct release builds, generate update.json after assembling, then print outputs
    if (flavor == "direct" && buildType == "release") {
        val apkPath = "${buildDirPath}/outputs/apk/${flavor}/${buildType}/tv-${flavor}-${buildType}.apk"
        val updateJsonPath = "${buildDirPath}/outputs/updateJson/update.json"
        
        // Create a task that always prints outputs (runs even when assemble/generateUpdateJson are up-to-date)
        val printOutputsTaskName = "printDirectReleaseOutputs"
        tasks.register(printOutputsTaskName) {
            doLast {
                println("\n" + "=".repeat(70))
                println("BUILD OUTPUTS")
                println("=".repeat(70))
                
                val apkFile = File(apkPath)
                if (apkFile.exists()) {
                    println("\n📱 Direct Release APK:")
                    println("   ${apkFile.absolutePath}")
                }
                
                val updateJsonFile = File(updateJsonPath)
                if (updateJsonFile.exists()) {
                    println("\n📄 Update JSON (for Direct Release):")
                    println("   ${updateJsonFile.absolutePath}")
                }
                
                println("\n" + "=".repeat(70) + "\n")
            }
        }
        
        tasks.named(assembleTaskName).configure {
            finalizedBy("generateUpdateJson", printOutputsTaskName)
        }
        
        tasks.named("generateUpdateJson").configure {
            finalizedBy(printOutputsTaskName)
        }
    } else if (flavor == "play" && buildType == "release") {
        // For Play release builds, print APK location after assembling
        val apkPath = "${buildDirPath}/outputs/apk/${flavor}/${buildType}/tv-${flavor}-${buildType}.apk"
        tasks.named(assembleTaskName).configure {
            doLast {
                val apkFile = File(apkPath)
                if (apkFile.exists()) {
                    println("\n" + "=".repeat(70))
                    println("BUILD OUTPUTS")
                    println("=".repeat(70))
                    println("\n📱 Play Release APK:")
                    println("   ${apkFile.absolutePath}")
                    println("\n" + "=".repeat(70) + "\n")
                }
            }
        }
    }
}