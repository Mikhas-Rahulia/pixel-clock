# ProGuard / R8 Optimization Rules for Pixel Clock

# Keep Kotlinx Serialization models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Services and BroadcastReceivers
-keep class com.example.pixelclock.alarm.AlarmReceiver { *; }
-keep class com.example.pixelclock.alarm.AlarmService { *; }
-keep class com.example.pixelclock.alarm.BootReceiver { *; }
-keep class com.example.pixelclock.timer.TimerService { *; }
-keep class com.example.pixelclock.timer.TimerReceiver { *; }
-keep class com.example.pixelclock.timer.TimerRingingActivity { *; }
-keep class com.example.pixelclock.alarm.AlarmRingingActivity { *; }
-keep class com.example.pixelclock.MainActivity { *; }
