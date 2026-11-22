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

# Room resolves the generated "<Database>_Impl" class by name and instantiates it
# through a reflective no-arg constructor lookup. Room's bundled rule only keeps
# the class itself, so R8 is free to strip that constructor.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ML Kit discovers its Firebase ComponentRegistrars by reflection from manifest
# meta-data. firebase-components only ships "-keep class * implements
# ComponentRegistrar" (class, no members), so R8 full mode (AGP 9+) strips the
# no-arg constructors. Discovery then skips them, MlKitContext lookup returns
# null and GmsBarcodeScanning.getClient() throws a NullPointerException inside
# mlkit_code_scanner.zzny.<init>.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# Keep the ML Kit common/vision registrars and the code scanner internals used by
# GmsBarcodeScanning.getClient().
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }