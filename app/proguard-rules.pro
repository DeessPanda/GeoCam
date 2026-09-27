# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the default proguard-android-optimize.txt file.

# CameraX
-keep class androidx.camera.** { *; }

# ExifInterface
-keep class androidx.exifinterface.** { *; }

# Keep our app classes (prevents over-aggressive stripping)
-keep class dev.geocam.app.** { *; }

# Kotlin coroutines / reflection used internally
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
