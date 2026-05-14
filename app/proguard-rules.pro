# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# StatusBarManager accessed via reflection in MainActivity
-keep class android.app.StatusBarManager {
    void expandNotificationsPanel();
}

# NotificationListenerService subclass discovered by the system
-keep class com.simple.elderlylauncher.service.NotificationService { *; }
