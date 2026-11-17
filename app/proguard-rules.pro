# libsu usa reflexión para arrancar el shell root
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
