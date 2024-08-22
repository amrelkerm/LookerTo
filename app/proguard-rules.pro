# Preserve class names for specific classes
-keep class com.example.lookerto.MainActivity { *; }

# Preserve methods with specific annotations
-keepclassmembers class ** {
    @com.example.lookerto.KeepMe *;
}

# Keep all public classes, interfaces, and enums in a specific package
-keep public class com.example.lookerto.api.** { *; }

# Keep native method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep the names of class members for classes that implement Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    static final android.os.Parcelable$Creator *;
}

# Do not strip any code in the support library
-dontwarn android.support.**
