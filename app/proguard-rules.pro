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
# Conservative keep rules for the R8 release build.
# ---------------------------------------------------------------------------

# Generics are needed across the AIDL binder (List<Partition>) and parcelables.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# libsu: uses reflection/IPC internally for Shell and RootService.
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Our root IPC surface: AIDL interface/stub, the parcelable, and the RootService
# (instantiated by the framework in the root process).
-keep class com.xiddoc.androidautox.IPhixitRoot { *; }
-keep class com.xiddoc.androidautox.IPhixitRoot$* { *; }
-keep class com.xiddoc.androidautox.PhixitRootService { *; }
-keep class com.xiddoc.androidautox.Partition { *; }

# Parcelable CREATORs (belt-and-suspenders; R8 usually keeps these).
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Reflection / XML-inflated third-party libs.
-keep class com.android.volley.** { *; }
-dontwarn com.android.volley.**
-keep class com.rm.rmswitch.** { *; }
-keep class com.romandanylyk.pageindicatorview.** { *; }
