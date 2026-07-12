package com.parentalcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parentalcontrol.auth.service.EmailSender;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([^&\\s]+)");

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_pwd_reset_test");
    registry.add("app.hmac.required", () -> "false");
    registry.add("app.password-reset.reset-url-base", () -> "https://parent.test/reset-password");
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private EmailSender emailSender;

  private String capturedToken;

  @BeforeEach
  void captureResetLinks() {
    capturedToken = null;
    doAnswer(
            invocation -> {
              String link = invocation.getArgument(1);
              Matcher matcher = TOKEN_PATTERN.matcher(link);
              if (matcher.find()) {
                capturedToken = matcher.group(1);
              }
              return null;
            })
        .when(emailSender)
        .sendPasswordResetEmail(anyString(), anyString());
  }

  @Test
  void forgotAndResetPassword() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "reset-user@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Reset",
                                  "lastName": "User",
                                  "consentAccepted": "true"
                                }
                                """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email": "reset-user@example.com"}
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(capturedToken).isNotBlank();

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "token": "%s",
                                  "password": "NewSecure456!"
                                }
                                """
                        .formatted(capturedToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "reset-user@example.com",
                                  "password": "NewSecure456!"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").exists());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "reset-user@example.com",
                                  "password": "SecurePass123!"
                                }
                                """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void forgotPasswordDoesNotRevealUnknownEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email": "nobody@example.com"}
                                """))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.message")
                .value("If an account exists for that email, a reset link has been sent."));
  }
}
