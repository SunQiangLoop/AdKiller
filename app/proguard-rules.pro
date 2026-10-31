# Keep annotations and generic signatures consumed by dependency reflection.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Native symbol names must continue to match bundled ML Kit libraries.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Android can recreate parcelables and invoke XML click callbacks reflectively.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# Optional compile-time annotations are not runtime requirements.
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
