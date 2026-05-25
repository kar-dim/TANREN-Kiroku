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