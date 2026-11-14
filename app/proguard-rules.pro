# NewPipeExtractor (uses reflection / Rhino JavaScript engine for signature deobfuscation)
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class com.grack.nanojson.** { *; }
-dontwarn org.mozilla.javascript.tools.**
-dontwarn org.mozilla.javascript.engine.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn javax.lang.model.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn com.google.protobuf.**
-keep class com.google.protobuf.** { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room entities
-keep class com.nocturne.player.data.db.** { *; }
