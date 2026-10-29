# ProGuard rules for Bataydar
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
