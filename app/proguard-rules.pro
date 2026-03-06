# =============================================================================
# VOID NOTE — ProGuard / R8 Rules
# =============================================================================
# R8 runs on every release build (isMinifyEnabled = true in build.gradle.kts).
# It renames classes and removes unused code — both break anything that uses
# reflection or relies on class names being stable at runtime.
#
# HOW TO READ THESE RULES:
#   -keep class X            → don't rename or remove class X
#   -keepclassmembers        → keep members (fields/methods) of matching classes
#   -keepnames               → keep names but still allow removal if unused
#   ** in a class path       → matches any class in that package (recursive)
#   { *; }                   → keep all members of the matched class
# =============================================================================


# =============================================================================
# 1. ROOM DATABASE
# =============================================================================
# Room uses reflection to map @Entity classes to SQLite table columns.
# If R8 renames a field like `val title: String`, Room can't match it to
# the "title" column in the database — causing a crash at startup.
#
# We keep all @Entity, @Dao, and @Database classes and their members intact.
# =============================================================================

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# TypeConverters are called by name at runtime
-keep class com.greenicephoenix.voidnote.data.local.converter.** { *; }


# =============================================================================
# 2. HILT (Dependency Injection)
# =============================================================================
# Hilt generates factory classes at compile time. R8 must not remove them
# because Hilt looks them up by generated name at runtime.
# The hilt-android library ships its own consumer ProGuard rules, but
# we add these as a belt-and-suspenders safeguard.
# =============================================================================

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Keep all Hilt-generated component and module classes
-keep class **_HiltComponents* { *; }
-keep class **_ComponentTreeDeps* { *; }
-keep class **Hilt_* { *; }


# =============================================================================
# 3. KOTLINX SERIALIZATION
# =============================================================================
# Serialization introspects @Serializable classes by name to read/write JSON.
# R8 renaming fields like `val noteId: String` will break JSON parsing.
#
# We keep all our data/export model classes that are @Serializable.
# =============================================================================

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** { *; }

# Keep all @Serializable classes in our package
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }

# The serializer companion objects R8 would otherwise remove
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}


# =============================================================================
# 4. ANDROID KEYSTORE & BIOMETRIC
# =============================================================================
# These use JCA (Java Cryptography Architecture) provider lookup by string name.
# R8 must not remove or rename the provider implementation classes.
# =============================================================================

-keep class android.security.keystore.** { *; }
-keep class androidx.biometric.** { *; }


# =============================================================================
# 5. WORKMANAGER (TrashCleanupWorker)
# =============================================================================
# WorkManager instantiates worker classes by their fully-qualified class name,
# which it reads from the job metadata saved to disk. If R8 renames the class,
# WorkManager can't re-instantiate it after an app restart and the job silently
# fails (or crashes). Keep all Worker subclasses by name.
# =============================================================================

-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# HiltWorkerFactory generated code
-keep class com.greenicephoenix.voidnote.data.worker.** { *; }


# =============================================================================
# 6. DATASTORE
# =============================================================================
# DataStore preferences use Proto or key-name lookups. Keep the preferences
# keys class so field names are stable.
# =============================================================================

-keep class androidx.datastore.** { *; }


# =============================================================================
# 7. DOMAIN & DATA MODELS
# =============================================================================
# Keep all domain models. They are serialized in backups, passed through
# navigation arguments, and logged in crash reports — all of which require
# stable class and field names.
# =============================================================================

-keep class com.greenicephoenix.voidnote.domain.model.** { *; }
-keep class com.greenicephoenix.voidnote.data.local.entity.** { *; }
-keep class com.greenicephoenix.voidnote.presentation.settings.** { *; }  # ExportModels


# =============================================================================
# 8. GENERAL KOTLIN & COROUTINES
# =============================================================================

# Kotlin metadata — needed for reflection-based libraries
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault

# Coroutines internal classes
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# Keep coroutine debug metadata in debug builds (stripped in release — fine)
-dontwarn kotlinx.coroutines.debug.*


# =============================================================================
# 9. SUPPRESS KNOWN SAFE WARNINGS
# =============================================================================
# Some libraries reference classes that don't exist on Android (they exist on
# the desktop JVM). R8 warns about them — we suppress since they're harmless.

-dontwarn java.lang.instrument.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.management.**