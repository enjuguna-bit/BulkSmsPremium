# Keep all classes that implement Application
-keep public class * extends android.app.Application

# Keep all activities
-keep public class * extends android.app.Activity

# Keep all fragments
-keep public class * extends androidx.fragment.app.Fragment

# Keep all views
-keep public class * extends android.view.View

# Keep all services
-keep public class * extends android.app.Service

# Keep all broadcast receivers
-keep public class * extends android.content.BroadcastReceiver

# Keep all content providers
-keep public class * extends android.content.ContentProvider

# Keep all support library classes
-keep class android.support.** { *; }
-keep interface android.support.** { *; }

# Keep all AndroidX classes
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Keep all Hilt classes
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }

# Keep Room database and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keepclassmembers class * {
    @androidx.room.* *;
}

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel

# Keep Parcelables
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep BuildConfig
-keep class com.bulksms.smsmanager.BuildConfig { *; }

# Keep the support library
-keep class android.support.v7.widget.** { *; }
-keep interface android.support.v7.widget.** { *; }
