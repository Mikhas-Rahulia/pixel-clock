# ProGuard / R8 Full-Mode Rules for Pixel Clock

# Keep only attributes required by kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations,Signature,InnerClasses,EnclosingMethod

# Strip debug/verbose/info logs in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
