# Helmet Guard ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
