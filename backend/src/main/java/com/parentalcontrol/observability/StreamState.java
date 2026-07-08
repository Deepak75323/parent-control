package com.parentalcontrol.observability;

/** Unified stream lifecycle states — grep logs with {@code STREAM_STATE}. */
public enum StreamState {
    IDLE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    SERVICE_STARTING,
    SERVICE_STARTED,
    PERMISSION_VERIFIED,
    CAPTURE_STARTING,
    CAPTURE_RUNNING,
    WEBRTC_CONNECTING,
    LOCAL_DESCRIPTION_SET,
    REMOTE_DESCRIPTION_SET,
    ICE_CONNECTING,
    CONNECTED,
    FIRST_FRAME_SENT,
    FIRST_FRAME_RECEIVED,
    STREAMING,
    STOPPING,
    STOPPED,
    FAILED
}
