package com.parentalcontrol.device.domain;

/** Continuous device trust state derived from Play Integrity + client signals. */
public enum DeviceTrustLevel {
    /** Score >= pairing threshold — full platform access. */
    TRUSTED,
    /** Degraded signals — monitored; some features may be limited later. */
    ELEVATED_RISK,
    /** Failed trust checks — restricted pending parent review. */
    UNTRUSTED,
    /** Hard block — device JWT requests rejected. */
    BLOCKED,
    /** Not yet evaluated (legacy rows / dev). */
    UNKNOWN
}
