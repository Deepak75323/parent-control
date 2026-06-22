# React Native
-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }

# WebRTC (stream-webrtc-android + react-native-webrtc)
-keep class org.webrtc.** { *; }
-keep class com.oney.WebRTCModule.** { *; }
-keep class com.childmonitor.capture.** { *; }
-keep class com.childmonitor.sync.** { *; }
-keep class com.childmonitor.fcm.** { *; }

# Child monitor native bridge
-keep class com.childmonitor.native.ChildMonitorNativeModule { *; }
-keep class com.childmonitor.** { *; }

# Firebase messaging
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
