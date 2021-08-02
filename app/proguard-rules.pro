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

#-injars libs/poishadow-all.jar
-keep class com.fasterxml.aalto.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.apache.commons.collections4.** { *; }
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class org.etsi.uri.x01903.v13.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.w3.x2000.x09.xmldsig.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }