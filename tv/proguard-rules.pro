# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ---------------------------------------------------------------------------
# Crash and ANR readability
# ---------------------------------------------------------------------------
# Play vitals traces are the primary signal for diagnosing ANRs in this app, and
# an obfuscated trace without line numbers is close to useless. Keeping these two
# attributes costs a little size; -renamesourcefileattribute puts back the file
# name obfuscation would otherwise strip, so frames still read as File.kt:123.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# kotlinx-serialization-core ships consumer rules covering the runtime itself, but
# the generated $$serializer classes and the Companion.serializer() accessors belong
# to this app's own models (ca.devmesh.seerrtv.model.*, SeerrApiService, UserProfile,
# DiagnosticsLog) and are only ever reached reflectively, so R8 cannot see the link.
# These are the upstream-recommended rules from the kotlinx.serialization README.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep `Companion` object fields of serializable classes, so serializer lookup does
# not have to fall back to getDeclaredClasses.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects, default and named alike.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# WebView JavaScript bridges
# ---------------------------------------------------------------------------
# Redundant with the @android.webkit.JavascriptInterface rule in
# proguard-android-optimize.txt, which is currently doing the work — verified by
# finding sendReady/sendStateChange/... intact in the minified DEX. Stated
# explicitly anyway because the failure mode is silent: the YouTube player
# (androidyoutubeplayer 13.0.0) drives playback from res/raw/ayp_youtube_player.html,
# which calls YouTubePlayerBridge.sendReady() and friends *by name* from JavaScript.
# Rename those methods and the player never reports ready — it just spins forever,
# with no crash and nothing in logcat to point at the cause.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
