# R8 rules for the release build.
#
# WHY MINIFICATION IS ON AT ALL: a Play Store app ships smaller and starts faster shrunk, and the
# rule data, the engine and the scoring weights are the product — leaving them fully named in the
# APK hands a competitor the whole thing for the price of one download. This is not security (an
# APK can always be read); it is the ordinary cost of copying, raised from minutes to hours.
#
# ⚠ THE RISK THIS FILE EXISTS TO MANAGE: R8 breaks code that finds things by NAME at runtime, and it
# breaks it at RUNTIME — a shrunk build compiles perfectly and then crashes on a phone. Every rule
# below names the exact thing that would break and why, so none of them can be deleted as "probably
# unnecessary" by someone who did not hit the crash.

# ---------------------------------------------------------------------------------------------
# kotlinx-serialization. The compiler plugin generates a `Companion.serializer()` and a
# `$$serializer` class for every @Serializable type, and they are found REFLECTIVELY. Every saved
# home and every draft is stored as JSON through these, so losing them means saved homes that will
# not open — the single worst failure this app can have.
# ---------------------------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.vastufirst.** {
    *** Companion;
}
-keepclasseswithmembers class com.vastufirst.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.vastufirst.**
-keepclassmembers class com.vastufirst.<1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.vastufirst.**$$serializer { *; }

# The shared DTOs and enums are named in stored JSON BY NAME (an enum is written as "KITCHEN").
# Renaming them silently changes the file format for every already-saved home.
-keep class com.vastufirst.shared.** { *; }

# ---------------------------------------------------------------------------------------------
# The versioned rule data is loaded from resources and mapped onto these types by name.
# ---------------------------------------------------------------------------------------------
-keep class com.vastufirst.rules.** { *; }

# ---------------------------------------------------------------------------------------------
# Koin resolves by TYPE, not by reflection over names, so it needs almost nothing — but the
# ViewModel constructors are called through androidx's factory and must survive.
# ---------------------------------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# ---------------------------------------------------------------------------------------------
# Coroutines: the debug agent probes for these and logs noisily when they are gone.
# ---------------------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---------------------------------------------------------------------------------------------
# Google Play Billing ships its own consumer rules, so nothing is needed for the checkout itself.
# This only silences the optional dependencies it declares and does not use here.
# ---------------------------------------------------------------------------------------------
-dontwarn com.google.**

# ---------------------------------------------------------------------------------------------
# ⚠ KEEP THE CRASH TRACE READABLE. Without this the stack trace a user emails us is a wall of
# one-letter class names, i.e. the crash reporter reports nothing. The mapping file stays out of the
# APK; SourceFile is renamed so it leaks no paths.
# ---------------------------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
