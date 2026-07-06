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

# ---- 腾讯 X5 (TBS) SDK 官方要求的混淆保留规则 ----
-dontwarn dalvik.**
-dontwarn com.tencent.smtt.**
-keep class com.tencent.smtt.** { *; }
-keep class com.tencent.tbs.** { *; }

# JS bridge：X5 的 addJavascriptInterface 反射调用带 @JavascriptInterface 的方法，
# release 包开了 minify，务必保留，否则 release 包里 JS 调不到 Android 方法
-keepclassmembers class com.yukon.litewebtv.engine.TvJsBridge {
    public *;
}