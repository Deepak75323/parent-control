package com.childmonitor.capture

/** Remote live stream types that need a typed foreground service on Android 14+. */
enum class StreamKind {
    CAMERA,
    AUDIO,
    SCREEN,
}
