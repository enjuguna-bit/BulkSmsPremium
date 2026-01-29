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

# Keep R classes
# -keep class com.bulksms.smsmanager.R { *; }
# -keep class com.bulksms.smsmanager.R$* { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep Retrofit classes
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Keep OkHttp classes
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Hilt classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Keep billing classes
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Keep biometric classes
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Keep security classes
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Keep Glide classes
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# Keep Apache POI classes
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**

# Keep PDFBox classes
-keep class org.apache.pdfbox.** { *; }
-dontwarn org.apache.pdfbox.**

# Keep OpenCSV classes
-keep class com.opencsv.** { *; }
-dontwarn com.opencsv.**

# Keep AWT classes (for PDFBox/graphbuilder)
-dontwarn java.awt.Shape
