package com.childmonitor.capture

/**
 * Only these callers may open the system MediaProjection (Cast) dialog.
 *
 * - [SETUP]: pairing / permission wizard while setup-in-progress or unpaired.
 * - [USER_GRANT]: child UI "Enable live view" / Grant screen capture (explicit tap).
 * - [PARENT_SCREEN_SHARE]: parent pressed Start Screen Sharing.
 *
 * Background recovery must never pass any of these.
 */
enum class ConsentLaunchSource {
    SETUP,
    USER_GRANT,
    PARENT_SCREEN_SHARE,
}
