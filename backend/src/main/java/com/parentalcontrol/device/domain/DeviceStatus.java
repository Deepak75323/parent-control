package com.parentalcontrol.device.domain;

public enum DeviceStatus {
    PENDING_PAIRING,
    ONLINE,
    /** Presence: device has not heartbeated recently — not the same as monitoring revoked. */
    OFFLINE,
    /** Monitoring explicitly revoked — device JWT invalidated via tokenVersion bump. */
    REVOKED,
    SUSPENDED
}
