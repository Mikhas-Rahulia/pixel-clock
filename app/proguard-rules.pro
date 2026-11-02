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
-keep class io.github.mikhasrahulia.pixelclock.alarm.AlarmReceiver { *; }
-keep class io.github.mikhasrahulia.pixelclock.alarm.AlarmService { *; }
-keep class io.github.mikhasrahulia.pixelclock.alarm.BootReceiver { *; }
-keep class io.github.mikhasrahulia.pixelclock.timer.TimerService { *; }
-keep class io.github.mikhasrahulia.pixelclock.timer.TimerReceiver { *; }
-keep class io.github.mikhasrahulia.pixelclock.timer.TimerRingingActivity { *; }
-keep class io.github.mikhasrahulia.pixelclock.alarm.AlarmRingingActivity { *; }
-keep class io.github.mikhasrahulia.pixelclock.MainActivity { *; }
