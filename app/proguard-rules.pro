# Add project specific ProGuard rules here.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

-keep class com.camlauncher.app.** { *; }
