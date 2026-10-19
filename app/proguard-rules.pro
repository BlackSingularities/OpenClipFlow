# Compose, AndroidX i Media3 dostarczają własne reguły konsumenckie.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# kotlinx.serialization – serializatory modelu projektu.
-keepclassmembers @kotlinx.serialization.Serializable class com.openclipflow.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.openclipflow.app.**$$serializer { *; }
-keepclassmembers class com.openclipflow.app.** {
    *** Companion;
}

# Media3: dekodery i rozszerzenia ładowane refleksją.
-dontwarn androidx.media3.**
