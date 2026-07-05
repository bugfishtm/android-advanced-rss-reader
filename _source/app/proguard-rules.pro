# ---- RSS Reader Advanced R8/ProGuard rules ------------------------------
# Keep enough for the bits that rely on reflection or resource/manifest
# class-name references. Activities, Views used in layouts and the app's own
# manifest components are kept automatically by AGP-generated rules.

# WorkManager instantiates the Worker by class name via reflection.
-keep class de.bugfish.rssreaderadvanced.work.RefreshWorker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# WorkManager is initialized automatically through androidx.startup's
# InitializationProvider, which reflectively instantiates each Initializer
# (and WorkManager's default Configuration) via its no-arg constructor.
# R8 full mode strips those constructors unless they are explicitly kept,
# which crashes the app on launch with:
#   Unable to get provider androidx.startup.InitializationProvider
#   Caused by: NoSuchMethodException: <init> []
-keep class androidx.startup.InitializationProvider { <init>(...); }
-keep class * extends androidx.startup.Initializer { <init>(...); }
-keep class androidx.work.** { *; }

# SearchView is referenced by name from res/menu (app:actionViewClass).
-keep class androidx.appcompat.widget.SearchView { *; }

# Data model classes (plain POJOs) — defensive; keeps stack traces tidy.
-keep class de.bugfish.rssreaderadvanced.data.** { *; }

# Keep line numbers and source file for readable crash reports (the mapping
# file translates the rest back).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
