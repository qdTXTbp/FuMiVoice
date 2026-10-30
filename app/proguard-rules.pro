# BASS/BASSMIDI/BASS_FX 的 Java 绑定通过 JNI FindClass/GetFieldID 反射字段，
# 不能改名；应用自身保留全部字段/方法，Compose 等库仍可被 R8 收缩。
-keep class com.fumi.voice.** { *; }
-keep class com.un4seen.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn org.jetbrains.annotations.**
