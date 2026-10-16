# Release uses R8 (isMinifyEnabled). Library consumer rules cover Room, Hilt,
# Kotlin serialization, and WorkManager. These keep manifest entry points and
# the worker class WorkManager loads by name.

-keep class com.example.localledger.LocalLedgerApplication { *; }
-keep class com.example.localledger.MainActivity { *; }
-keep class com.example.localledger.notification.PaymentNotificationListenerService { *; }
-keep class com.example.localledger.notification.DraftActionReceiver { *; }
-keep class com.example.localledger.data.work.RecurringTransactionWorker { *; }

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-keepclassmembers class com.example.localledger.core.storage.AppSettings {
    <init>(...);
}
-keepclassmembers class com.example.localledger.core.storage.ThemeMode {
    <init>(...);
    **[] values();
    ** valueOf(java.lang.String);
}
