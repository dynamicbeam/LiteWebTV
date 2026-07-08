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

# GeckoView 传递依赖引入的 SnakeYAML 引用了 Android 上不存在的 java.beans 包
-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.**

# -----------------------------------------------------------
# GeckoView 保护规则
# GeckoView 原生代码通过 JNI 反射调用 Java 层，R8 混淆会
# 导致找不到类/方法从而 SIGSEGV 崩溃（fault addr 0x0）
# -----------------------------------------------------------

# 保留所有 GeckoView 类及其全部成员（原生 JNI 代码直接访问）
-keep class org.mozilla.geckoview.** { *; }

# 保留应用中实现了 GeckoView 接口的类（作为回调传给原生层）
-keep class com.yukon.litewebtv.engine.** { *; }
-keep class com.yukon.litewebtv.MainActivity { *; }

# 保留所有匿名内部类（object : GeckoSession.NavigationDelegate 等）
-keepclassmembers class * {
    @org.mozilla.geckoview.** *;
}
-keep class * implements org.mozilla.geckoview.GeckoSession$NavigationDelegate { *; }
-keep class * implements org.mozilla.geckoview.GeckoSession$ProgressDelegate { *; }
-keep class * implements org.mozilla.geckoview.GeckoSession$PermissionDelegate { *; }
-keep class * implements org.mozilla.geckoview.WebExtension$MessageDelegate { *; }
-keep class * implements org.mozilla.geckoview.WebExtension$PortDelegate { *; }

# 保留 JNI 原生方法声明
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留反射所需的元数据
-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature, Exceptions