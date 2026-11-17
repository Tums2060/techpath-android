# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.techpath.**$$serializer { *; }
-keepclassmembers class com.techpath.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
