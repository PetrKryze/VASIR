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

-keep public class * extends androidx.appcompat.app.AppCompatActivity

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

-keepnames class * implements java.io.Serializable

# Explicitly preserve all serialization members. The Serializable interface
# is only a marker interface, so it wouldn't save them.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --------------------- Proguard config for poishadow.all library -----------------------
# Config found at: https://stackoverflow.com/questions/44323942/android-proguard-apache-poi

#-injars libs/poishadow-all.jar
-keep class com.fasterxml.aalto.** { *; }
#-keep class com.microsoft.schemas.** { *; }
-keep class org.apache.commons.collections4.** { *; }
-keep class org.apache.commons.compress.archivers.** { *; }
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class org.etsi.uri.x01903.v13.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.w3.x2000.x09.xmldsig.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }

-dontwarn org.apache.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn com.microsoft.schemas.**
-dontwarn com.graphbuilder.**
-dontnote org.apache.**
-dontnote org.openxmlformats.schemas.**
-dontnote org.etsi.**
-dontnote org.w3.**
-dontnote com.microsoft.schemas.**
-dontnote com.graphbuilder.**

-keeppackagenames org.apache.poi.ss.formula.function

-keep class com.microsoft.schemas.office.office.impl.CTIdMapImpl { *; }
-keep class com.microsoft.schemas.office.office.impl.CTShapeLayoutImpl { *; }
-keep class com.microsoft.schemas.vml.impl.CTShadowImpl { *; }
-keep class com.microsoft.schemas.vml.impl.CTFillImpl { *; }
-keep class com.microsoft.schemas.vml.impl.CTPathImpl { *; }
-keep class com.microsoft.schemas.vml.impl.CTShapeImpl { *; }
-keep class com.microsoft.schemas.vml.impl.CTShapetypeImpl { *; }
-keep class com.microsoft.schemas.vml.impl.CTStrokeImpl { *; }
-keep class com.microsoft.schemas.vml.impl.CTTextboxImpl { *; }
-keep class com.microsoft.schemas.office.excel.impl.CTClientDataImpl { *; }

##---------------Begin: proguard configuration for Gson  ----------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
#-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class com.google.gson.examples.android.model.** { <fields>; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

##---------------End: proguard configuration for Gson  ----------