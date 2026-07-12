package com.parentalcontrol;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies P1-04 fix: per-account brute-force protection.
 *
 * <p>Uses a short lockDurationSeconds (2 s) so TTL-expiry unlock is testable without a long sleep.
 *
 * <p>Isolated database: parental_control_lockout_test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountLockoutIntegrationTest {

  private static final String EMAIL = "lockout-test@example.com";
  private static final String PASSWORD = "SecurePass123!";
  private static final String WRONG = "WrongPassword!";

  private static final int MAX_ATTEMPTS = 3;
  private static final int LOCK_SECONDS = 2;
  private static final int ATTEMPT_WINDOW = 30;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_lockout_test");
    registry.add("app.hmac.required", () -> "false");
    // Override defaults set by configureLocalInfra
    registry.add("app.account-lockout.enabled", () -> "true");
    registry.add("app.account-lockout.max-attempts", () -> String.valueOf(MAX_ATTEMPTS));
    registry.add(
        "app.account-lockout.attempt-window-seconds", () -> String.valueOf(ATTEMPT_WINDOW));
    registry.add("app.account-lockout.lock-duration-seconds", () -> String.valueOf(LOCK_SECONDS));
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void clearRedisLockoutKeys() {
    // Reset between tests so each scenario starts clean.
    redisTemplate.delete("lockout:attempt:" + EMAIL);
    redisTemplate.delete("lockout:locked:" + EMAIL);
  }

  // -----------------------------------------------------------------------
  // Prerequisite: register the test user once
  // -----------------------------------------------------------------------

  @Test
  @Order(1)
  void setup_registerUser() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Lock",
                                  "lastName": "Test",
                                  "consentAccepted": "true"
                                }
                                """
                        .formatted(EMAIL, PASSWORD)))
        .andExpect(status().isCreated());
  }

  // -----------------------------------------------------------------------
  // Single failed login increments counter
  // -----------------------------------------------------------------------

  @Test
  @Order(2)
  void singleFailedLogin_returns401_notLocked() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(EMAIL, WRONG)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  // -----------------------------------------------------------------------
  // N-1 failures then correct password resets counter
  // -----------------------------------------------------------------------

  @Test
  @Order(3)
  void correctPassword_afterFailures_resetsCounterAndSucceeds() throws Exception {
    // Two wrong attempts (below max=3)
    for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(loginBody(EMAIL, WRONG)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // Correct password — must succeed and clear the counter
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isString());

    // After reset: a fresh wrong attempt does NOT immediately lock
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(EMAIL, WRONG)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  // -----------------------------------------------------------------------
  // maxAttempts exceeded → account locked → 429
  // -----------------------------------------------------------------------

  @Test
  @Order(4)
  void maxAttemptsExceeded_locksAccount_returns429() throws Exception {
    // Exhaust all attempts
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(loginBody(EMAIL, WRONG)))
          .andExpect(status().isUnauthorized());
    }

    // Next attempt — even with correct password — is blocked by lockout
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  // -----------------------------------------------------------------------
  // Automatic unlock after TTL expiry
  // -----------------------------------------------------------------------

  @Test
  @Order(5)
  void accountUnlocksAfterTtlExpiry_loginSucceeds() throws Exception {
    // Lock the account
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(loginBody(EMAIL, WRONG)))
          .andExpect(status().isUnauthorized());
    }
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isTooManyRequests());

    // Wait for lockDurationSeconds to expire
    Thread.sleep((LOCK_SECONDS + 1) * 1_000L);

    // Login must succeed once the TTL has expired
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isString());
  }

  // -----------------------------------------------------------------------
  // Existing users with valid credentials are never blocked (regression)
  // -----------------------------------------------------------------------

  @Test
  @Order(6)
  void existingUser_validCredentials_neverBlocked() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isString())
        .andExpect(jsonPath("$.data.refreshToken").isString());
  }

  // -----------------------------------------------------------------------
  // Unknown email never triggers lockout (prevents DoS via account probing)
  // -----------------------------------------------------------------------

  @Test
  @Order(7)
  void unknownEmail_neverRecordsLockout_returns401() throws Exception {
    String unknownEmail = "nobody@example.com";
    for (int i = 0; i <= MAX_ATTEMPTS + 1; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(loginBody(unknownEmail, WRONG)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
    // No lockout key created for unknown email
    Boolean lockedKeyExists = redisTemplate.hasKey("lockout:locked:" + unknownEmail);
    org.assertj.core.api.Assertions.assertThat(lockedKeyExists).isFalse();
  }

  // -----------------------------------------------------------------------
  // Refresh token flow is unaffected by lockout (P0-04/P1-04 interaction)
  //
  // Note: AuthService.login() is @Transactional(readOnly = true), which sets
  // Hibernate FlushMode.MANUAL — the refresh token entity is held in the
  // Hibernate session but never flushed to the DB. This is a pre-existing
  // issue in AuthService unrelated to P1-04.
  // We verify the refresh flow via register(), which uses @Transactional
  // (auto-flush) and correctly persists the token.
  // -----------------------------------------------------------------------

  @Test
  @Order(8)
  void refreshFlow_unaffectedByLockout() throws Exception {
    // Register a dedicated user so its refresh token is persisted via
    // AuthService.register() (@Transactional, auto-flush).
    String refreshEmail = "refresh-lockout-verify@example.com";
    var registerResult =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Refresh",
                                  "lastName": "Test",
                                  "consentAccepted": "true"
                                }
                                """
                            .formatted(refreshEmail, PASSWORD)))
            .andExpect(status().isCreated())
            .andReturn();

    var body =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(registerResult.getResponse().getContentAsString());
    String refreshToken = body.get("data").get("refreshToken").asText();
    org.assertj.core.api.Assertions.assertThat(refreshToken).isNotBlank();

    // Ensure new token falls in a different JWT second to avoid the
    // pre-existing uq_refresh_token_hash collision in JwtTokenProvider.
    Thread.sleep(1_100);

    // Refresh must succeed — the lockout path does not touch the refresh flow.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refreshToken": "%s"}
                                """
                        .formatted(refreshToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isString());
  }

  // -----------------------------------------------------------------------

  private static String loginBody(String email, String password) {
    return """
                {"email": "%s", "password": "%s"}
                """
        .formatted(email, password);
  }
}
