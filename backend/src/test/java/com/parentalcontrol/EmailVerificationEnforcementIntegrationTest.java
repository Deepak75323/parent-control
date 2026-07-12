package com.parentalcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parentalcontrol.auth.service.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies P0-04 fix: login must be blocked for unverified accounts when {@code
 * app.email-verification.enforcement-enabled=true}.
 *
 * <p>Uses its own isolated database (parental_control_email_enforcement_test) so it does not
 * interfere with AuthIntegrationTest or PasswordResetIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmailVerificationEnforcementIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_email_enforcement_test");
    registry.add("app.hmac.required", () -> "false");
    // Override the default enforcement=false set by configureLocalInfra
    registry.add("app.email-verification.enforcement-enabled", () -> "true");
    registry.add(
        "app.email-verification.verify-url-base", () -> "https://parent.test/verify-email");
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private EmailSender emailSender;

  private String capturedVerifyLink;

  @BeforeEach
  void captureVerificationLinks() {
    capturedVerifyLink = null;
    reset(emailSender);
    doAnswer(
            invocation -> {
              capturedVerifyLink = invocation.getArgument(1, String.class);
              return null;
            })
        .when(emailSender)
        .sendEmailVerificationEmail(anyString(), anyString());
  }

  // -----------------------------------------------------------------------
  // Core fix: unverified user is blocked
  // -----------------------------------------------------------------------

  @Test
  @Order(1)
  void unverifiedUser_cannotLogin_returns403EmailNotVerified() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "unverified@enforcement-test.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Unverified",
                                  "lastName": "User",
                                  "consentAccepted": "true"
                                }
                                """))
        .andExpect(status().isCreated());

    // Verification email was sent (enforcement enabled)
    assertThat(capturedVerifyLink).isNotBlank();

    // Login without verifying — must be rejected
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "unverified@enforcement-test.com",
                                  "password": "SecurePass123!"
                                }
                                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  // -----------------------------------------------------------------------
  // Happy path: verify then login
  // -----------------------------------------------------------------------

  @Test
  @Order(2)
  void verifiedUser_canLogin_returns200WithTokens() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "verified@enforcement-test.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Verified",
                                  "lastName": "User",
                                  "consentAccepted": "true"
                                }
                                """))
        .andExpect(status().isCreated());

    assertThat(capturedVerifyLink).isNotBlank();
    String token = extractToken(capturedVerifyLink);

    // Verify email
    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"token\":\"%s\"}", token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // Login must now succeed
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "verified@enforcement-test.com",
                                  "password": "SecurePass123!"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").isString())
        .andExpect(jsonPath("$.data.refreshToken").isString())
        .andExpect(jsonPath("$.data.profile.emailVerified").value(true));
  }

  // -----------------------------------------------------------------------
  // Existing flows must not be broken by the new check
  // -----------------------------------------------------------------------

  @Test
  @Order(3)
  void wrongPassword_stillReturns401InvalidCredentials() throws Exception {
    // Register and verify a user
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "wrongpw@enforcement-test.com",
                                  "password": "SecurePass123!",
                                  "firstName": "WrongPw",
                                  "lastName": "User",
                                  "consentAccepted": "true"
                                }
                                """))
        .andExpect(status().isCreated());

    // Wrong password — must still be INVALID_CREDENTIALS, not EMAIL_NOT_VERIFIED
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "wrongpw@enforcement-test.com",
                                  "password": "WrongPassword!"
                                }
                                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @Order(4)
  void forgotPassword_worksRegardlessOfVerificationStatus() throws Exception {
    // Register without verifying
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "forgotpw@enforcement-test.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Forgot",
                                  "lastName": "Pw",
                                  "consentAccepted": "true"
                                }
                                """))
        .andExpect(status().isCreated());

    // Forgot-password must not be gated on email verification
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"forgotpw@enforcement-test.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @Order(5)
  void refreshToken_worksAfterVerification() throws Exception {
    // Register and verify
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "refresh@enforcement-test.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Refresh",
                                  "lastName": "User",
                                  "consentAccepted": "true"
                                }
                                """))
        .andExpect(status().isCreated());

    assertThat(capturedVerifyLink).isNotBlank();
    String token = extractToken(capturedVerifyLink);

    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"token\":\"%s\"}", token)))
        .andExpect(status().isOk());

    // Login to get tokens
    var loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "email": "refresh@enforcement-test.com",
                                  "password": "SecurePass123!"
                                }
                                """))
            .andExpect(status().isOk())
            .andReturn();

    var body =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(loginResult.getResponse().getContentAsString());
    String refreshToken = body.get("data").get("refreshToken").asText();
    assertThat(refreshToken).isNotBlank();

    // JwtTokenProvider encodes iat at second precision; sleeping >1 s ensures the
    // replacement token gets a different iat and avoids a uq_refresh_token_hash
    // collision — a pre-existing issue in JwtTokenProvider unrelated to P0-04.
    Thread.sleep(1_100);

    // Refresh must succeed — refresh flow is unaffected by the email enforcement fix
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"refreshToken\":\"%s\"}", refreshToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isString());
  }

  // -----------------------------------------------------------------------

  private static String extractToken(String verifyLink) {
    int idx = verifyLink.lastIndexOf("token=");
    assertThat(idx).as("verify link must contain 'token='").isGreaterThanOrEqualTo(0);
    return verifyLink.substring(idx + 6);
  }
}
