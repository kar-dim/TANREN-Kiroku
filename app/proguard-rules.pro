# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    kotlinx.serialization.descriptors.SerialDescriptor descriptor;
}
-keep,includedescriptorclasses class gr.dkaratzas.tanrenkiroku.**$$serializer { *; }
-keepclassmembers class gr.dkaratzas.tanrenkiroku.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit & Firebase Component Discovery (prevents stripping of reflective constructors)
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
    public java.util.List getComponents();
}
-keep class com.google.mlkit.** { *; }

# CameraX
-dontwarn androidx.camera.**
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }