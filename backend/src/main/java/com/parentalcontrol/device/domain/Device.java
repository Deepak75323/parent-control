package com.parentalcontrol.device.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "devices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device extends BaseEntity {

  @Column(name = "parent_profile_id", nullable = false)
  private UUID parentProfileId;

  @Column(name = "child_profile_id")
  private UUID childProfileId;

  @Column(name = "device_name", nullable = false)
  private String deviceName;

  @Column(name = "device_model")
  private String deviceModel;

  @Column(name = "os_version")
  private String osVersion;

  @Column(name = "app_version")
  private String appVersion;

  @Column(name = "android_id", unique = true)
  private String androidId;

  @Column(name = "fcm_token")
  private String fcmToken;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private DeviceStatus status = DeviceStatus.PENDING_PAIRING;

  @Column(name = "battery_level")
  private Integer batteryLevel;

  @Column(name = "is_charging")
  private Boolean charging;

  @Column(name = "network_type")
  private String networkType;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Column(name = "last_latitude")
  private Double lastLatitude;

  @Column(name = "last_longitude")
  private Double lastLongitude;

  @Column(name = "foreground_app")
  private String foregroundApp;

  @Column(name = "device_owner_active", nullable = false)
  @Builder.Default
  private boolean deviceOwnerActive = false;

  @Column(name = "device_admin_active", nullable = false)
  @Builder.Default
  private boolean deviceAdminActive = false;

  @Column(name = "media_projection_granted", nullable = false)
  @Builder.Default
  private boolean mediaProjectionGranted = false;

  /** Child-reported consent lifecycle — binder cannot survive process death. */
  @Column(name = "media_projection_consent_state", nullable = false)
  @Builder.Default
  private String mediaProjectionConsentState = MediaProjectionConsentState.NEVER_GRANTED.name();

  @Column(name = "silent_screen_capable", nullable = false)
  @Builder.Default
  private boolean silentScreenCapable = false;

  @Column(name = "protection_level", nullable = false)
  @Builder.Default
  private String protectionLevel = "STANDARD";

  @Column(name = "device_owner_provisioned_at")
  private Instant deviceOwnerProvisionedAt;

  /** Per-device HMAC signing key (issued at pair; rotate via /device/rotate-hmac). */
  @Column(name = "device_hmac_key")
  private String deviceHmacKey;

  @Column(name = "kiosk_mode_active", nullable = false)
  @Builder.Default
  private boolean kioskModeActive = false;

  @Column(name = "accessibility_enabled", nullable = false)
  @Builder.Default
  private boolean accessibilityEnabled = false;

  /** Runtime AccessibilityService connected (may be false during brief restart gaps). */
  @Column(name = "accessibility_service_connected", nullable = false)
  @Builder.Default
  private boolean accessibilityServiceConnected = false;

  /** Incremented on re-pair / revoke to invalidate prior device JWTs. */
  @Column(name = "token_version", nullable = false)
  @Builder.Default
  private long tokenVersion = 0L;

  @Enumerated(EnumType.STRING)
  @Column(name = "trust_level", nullable = false)
  @Builder.Default
  private DeviceTrustLevel trustLevel = DeviceTrustLevel.UNKNOWN;

  @Column(name = "trust_score", nullable = false)
  @Builder.Default
  private int trustScore = 0;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "trust_signals_json")
  private Map<String, Object> trustSignalsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "integrity_verdict_json")
  private Map<String, Object> integrityVerdictJson;

  @Column(name = "last_trust_evaluated_at")
  private Instant lastTrustEvaluatedAt;

  @Column(name = "trust_violation_count", nullable = false)
  @Builder.Default
  private int trustViolationCount = 0;

  @Column(name = "oem_vendor")
  private String oemVendor;

  @Column(name = "android_sdk_int")
  private Integer androidSdkInt;

  @Column(name = "device_readiness_score", nullable = false)
  @Builder.Default
  private int deviceReadinessScore = 0;

  @Column(name = "android16_readiness_score", nullable = false)
  @Builder.Default
  private int android16ReadinessScore = 0;

  @Column(name = "oem_setup_complete", nullable = false)
  @Builder.Default
  private boolean oemSetupComplete = false;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "oem_diagnostics_json")
  private Map<String, Object> oemDiagnosticsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "oem_failure_classifications")
  private List<String> oemFailureClassifications;

  @Column(name = "last_certification_at")
  private Instant lastCertificationAt;

  @Column(name = "certification_status")
  private String certificationStatus;

  @Column(name = "certification_score")
  private Integer certificationScore;

  /** bcrypt hash of the child device login password (copied from pairing code at pair time). */
  @Column(name = "child_login_password_hash")
  private String childLoginPasswordHash;
}
