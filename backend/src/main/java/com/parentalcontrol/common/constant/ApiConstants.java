package com.parentalcontrol.common.constant;

/**
 * Central API path prefixes, exact routes, and Ant-style matcher patterns. Use these in security
 * config, filters, interceptors, and controllers so route strings are not duplicated as literals.
 */
public final class ApiConstants {

  public static final String API_V1 = "/api/v1";
  public static final String API_V1_PREFIX = API_V1 + "/";
  public static final String API_V1_SERVLET = API_V1 + "/*";

  // ── Auth ──────────────────────────────────────────────────────────────
  public static final String AUTH = API_V1 + "/auth";
  public static final String AUTH_PREFIX = AUTH + "/";
  public static final String AUTH_ANT = AUTH + "/**";
  public static final String AUTH_REGISTER = AUTH + "/register";
  public static final String AUTH_LOGIN = AUTH + "/login";
  public static final String AUTH_REFRESH = AUTH + "/refresh";
  public static final String AUTH_LOGOUT = AUTH + "/logout";
  public static final String AUTH_FORGOT_PASSWORD = AUTH + "/forgot-password";
  public static final String AUTH_RESET_PASSWORD = AUTH + "/reset-password";
  public static final String AUTH_VERIFY_EMAIL = AUTH + "/verify-email";
  public static final String AUTH_ACCOUNT = AUTH + "/account";

  // ── Child device (singular) ───────────────────────────────────────────
  public static final String DEVICE = API_V1 + "/device";
  public static final String DEVICE_PREFIX = DEVICE + "/";
  public static final String DEVICE_ANT = DEVICE + "/**";
  public static final String DEVICE_PAIR = DEVICE + "/pair";
  public static final String DEVICE_HEARTBEAT = DEVICE + "/heartbeat";
  public static final String DEVICE_TELEMETRY = DEVICE + "/telemetry";
  public static final String DEVICE_TELEMETRY_ANT = DEVICE_TELEMETRY + "/**";
  public static final String DEVICE_ANALYTICS = DEVICE + "/analytics";
  public static final String DEVICE_ANALYTICS_ANT = DEVICE_ANALYTICS + "/**";
  public static final String DEVICE_TAMPER_VERIFY = DEVICE + "/tamper/verify";
  public static final String DEVICE_TAMPER_ATTEMPT = DEVICE + "/tamper/attempt";
  public static final String DEVICE_TAMPER_OFFLINE_PIN = DEVICE + "/tamper/offline-pin";
  public static final String DEVICE_TAMPER_ADMIN_DISABLED = DEVICE + "/tamper/admin-disabled";
  public static final String DEVICE_TIME_EXTENSION_REQUESTS = DEVICE + "/time-extension-requests";

  /**
   * @deprecated prefer {@link #DEVICE_TAMPER_VERIFY}
   */
  public static final String TAMPER_VERIFY = DEVICE_TAMPER_VERIFY;

  // ── Parent devices (plural) ───────────────────────────────────────────
  public static final String DEVICES = API_V1 + "/devices";
  public static final String DEVICES_ANT = DEVICES + "/**";
  public static final String DEVICES_PAIRING_CODE = DEVICES + "/pairing-code";

  // ── Onboarding ────────────────────────────────────────────────────────
  public static final String ONBOARDING = API_V1 + "/onboarding";
  public static final String ONBOARDING_PREFIX = ONBOARDING + "/";
  public static final String ONBOARDING_ANT = ONBOARDING + "/**";

  // ── Billing ───────────────────────────────────────────────────────────
  public static final String BILLING = API_V1 + "/billing";
  public static final String BILLING_ANT = BILLING + "/**";
  public static final String BILLING_WEBHOOKS = BILLING + "/webhooks";
  public static final String BILLING_WEBHOOKS_PREFIX = BILLING_WEBHOOKS + "/";
  public static final String BILLING_WEBHOOKS_ANT = BILLING_WEBHOOKS + "/**";
  public static final String BILLING_WEBHOOKS_STRIPE = BILLING_WEBHOOKS + "/stripe";

  // ── Monitoring ────────────────────────────────────────────────────────
  public static final String MONITORING = API_V1 + "/monitoring";
  public static final String MONITORING_ANT = MONITORING + "/**";
  public static final String MONITORING_SIGNALING = MONITORING + "/signaling";
  public static final String MONITORING_SIGNALING_PREFIX = MONITORING_SIGNALING + "/";
  public static final String MONITORING_SIGNALING_ANT = MONITORING_SIGNALING + "/**";
  public static final String MONITORING_WEBRTC_CONFIG = MONITORING + "/webrtc-config";
  public static final String MONITORING_WEBRTC_ICE_REPORT = MONITORING + "/webrtc-ice-report";

  // ── Child / events ────────────────────────────────────────────────────
  public static final String CHILD = API_V1 + "/child";
  public static final String CHILD_PREFIX = CHILD + "/";
  public static final String CHILD_ANT = CHILD + "/**";
  public static final String CHILD_STREAMING = CHILD + "/streaming";

  public static final String EVENTS = API_V1 + "/events";
  public static final String EVENTS_PREFIX = EVENTS + "/";
  public static final String EVENTS_ANT = EVENTS + "/**";

  // ── Parent resources ──────────────────────────────────────────────────
  public static final String ALERTS = API_V1 + "/alerts";
  public static final String ALERTS_ANT = ALERTS + "/**";

  public static final String POLICIES = API_V1 + "/policies";
  public static final String POLICIES_ANT = POLICIES + "/**";

  public static final String AUDIT = API_V1 + "/audit";
  public static final String AUDIT_ANT = AUDIT + "/**";

  public static final String PARENT = API_V1 + "/parent";
  public static final String PARENT_ANT = PARENT + "/**";
  public static final String PARENT_PIN = PARENT + "/pin";
  public static final String PARENT_DELETED_MESSAGES_PIN = PARENT + "/deleted-messages-pin";
  public static final String PARENT_CAMERA_PASSWORD = PARENT + "/camera-password";
  public static final String PARENT_AUTO_GREETING = PARENT + "/auto-greeting";
  public static final String PARENT_CHILD_LOGIN_PASSWORD = PARENT + "/child-login-password";
  public static final String PARENT_FAMILY_HANDLE = PARENT + "/family-handle";
  public static final String PARENT_READ_RECEIPTS = PARENT + "/read-receipts";
  public static final String PARENT_SHARE_PRESENCE = PARENT + "/share-presence";
  public static final String PARENT_FRIENDS = PARENT + "/friends";
  public static final String PARENT_ANALYTICS = PARENT + "/analytics";
  public static final String PARENT_ANALYTICS_ANT = PARENT_ANALYTICS + "/**";

  public static final String TIME_EXTENSION_REQUESTS = API_V1 + "/time-extension-requests";
  public static final String TIME_EXTENSION_REQUESTS_ANT = TIME_EXTENSION_REQUESTS + "/**";
  public static final String TIME_EXTENSION_REQUESTS_PENDING = TIME_EXTENSION_REQUESTS + "/pending";

  public static final String CHAT = API_V1 + "/chat";
  public static final String CHAT_ANT = CHAT + "/**";

  public static final String ADMIN = API_V1 + "/admin";
  public static final String ADMIN_ANT = ADMIN + "/**";
  public static final String ADMIN_CERTIFICATION = ADMIN + "/certification";
  public static final String ADMIN_CHAOS = ADMIN + "/chaos";

  // ── Legacy / alternate prefixes (documented contract) ─────────────────
  public static final String STREAMING = API_V1 + "/streaming";
  public static final String SIGNALING = API_V1 + "/signaling";
  public static final String WEBRTC = API_V1 + "/webrtc";

  // ── Docs / actuator / websockets ──────────────────────────────────────
  public static final String SWAGGER_UI = "/swagger-ui";
  public static final String SWAGGER_UI_ANT = SWAGGER_UI + "/**";
  public static final String SWAGGER_UI_HTML = "/swagger-ui.html";
  public static final String API_DOCS = "/api-docs";
  public static final String API_DOCS_ANT = API_DOCS + "/**";
  public static final String V3_API_DOCS = "/v3/api-docs";
  public static final String V3_API_DOCS_ANT = V3_API_DOCS + "/**";

  public static final String ACTUATOR = "/actuator";
  public static final String ACTUATOR_HEALTH = ACTUATOR + "/health";
  public static final String ACTUATOR_HEALTH_ANT = ACTUATOR_HEALTH + "/**";
  public static final String ACTUATOR_INFO = ACTUATOR + "/info";
  public static final String ACTUATOR_PROMETHEUS = ACTUATOR + "/prometheus";

  public static final String WS = "/ws";
  public static final String WS_SLASH = WS + "/";
  public static final String WS_ANT = WS + "/**";
  public static final String WS_NATIVE = "/ws-native";
  public static final String WS_NATIVE_ANT = WS_NATIVE + "/**";

  /** Paths permitted without authentication (Spring Security Ant matchers). */
  public static final String[] PUBLIC_PATHS = {
    AUTH_REGISTER,
    AUTH_LOGIN,
    AUTH_REFRESH,
    AUTH_LOGOUT,
    AUTH_FORGOT_PASSWORD,
    AUTH_RESET_PASSWORD,
    AUTH_VERIFY_EMAIL,
    DEVICE_PAIR,
    ONBOARDING_ANT,
    BILLING_WEBHOOKS_ANT,
    // P2-02: Swagger / OpenAPI are NOT public — require ROLE_ADMIN (see SecurityConfig).
    ACTUATOR_HEALTH,
    ACTUATOR_HEALTH_ANT,
    ACTUATOR_INFO,
    ACTUATOR_PROMETHEUS,
    WS,
    WS_ANT,
    WS_NATIVE,
    WS_NATIVE_ANT
  };

  /** OpenAPI / Swagger UI — authenticated admin only (P2-02). */
  public static final String[] DOCS_PATHS = {
    SWAGGER_UI_ANT, SWAGGER_UI_HTML, API_DOCS_ANT, V3_API_DOCS_ANT
  };

  private ApiConstants() {}
}
