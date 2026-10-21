# Release uses R8 (isMinifyEnabled). Library consumer rules cover Room, Hilt,
# Kotlin serialization, and WorkManager. These keep manifest entry points and
# the worker class WorkManager loads by name.

-keep class com.example.localledger.LocalLedgerApplication { *; }
-keep class com.example.localledger.MainActivity { *; }
-keep class com.example.localledger.notification.PaymentNotificationListenerService { *; }
-keep class com.example.localledger.notification.Hilt_PaymentNotificationListenerService { *; }
-keep class com.example.localledger.notification.PaymentNotificationListenerService_GeneratedInjector { *; }
-keep class com.example.localledger.notification.NotificationListenerEntryPoint { *; }
-keep class * implements com.example.localledger.notification.NotificationListenerEntryPoint { *; }
-keep class com.example.localledger.notification.DraftActionReceiver { *; }
-keep class com.example.localledger.notification.Hilt_DraftActionReceiver { *; }
-keep class com.example.localledger.notification.DraftActionReceiver_GeneratedInjector { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class com.example.localledger.data.work.RecurringTransactionWorker { *; }

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepclassmembers class com.example.localledger.core.storage.AppSettings {
    <init>(...);
}
-keep class com.example.localledger.core.storage.AppSettings$$serializer { *; }
-keep class com.example.localledger.notification.NotificationDiagnostic { *; }
-keep class com.example.localledger.notification.NotificationDiagnostic$$serializer { *; }
-keepclassmembers class com.example.localledger.core.storage.ThemeMode {
    <init>(...);
    **[] values();
    ** valueOf(java.lang.String);
}
