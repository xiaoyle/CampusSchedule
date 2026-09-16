-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class **$$serializer { *; }
-keepclassmembers class cn.campus.core.** { *** Companion; }
-keep @androidx.room.Entity class * { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.apache.commons.logging.**
# PDFBox can optionally decode JPEG 2000 through Gemalto when that separate
# decoder is installed. The app does not bundle or invoke that optional path.
-dontwarn com.gemalto.jp2.**
