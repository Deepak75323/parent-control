package com.parentalcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountDeletionIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_delete_test");
    registry.add("app.hmac.required", () -> "false");
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  private static String accessToken;

  @Test
  @Order(1)
  void registerAndLogin() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "delete-me@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Delete",
                                  "lastName": "Me",
                                  "consentAccepted": "true"
                                }
                                """))
        .andExpect(status().isCreated());

    var login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "email": "delete-me@example.com",
                                  "password": "SecurePass123!"
                                }
                                """))
            .andExpect(status().isOk())
            .andReturn();

    accessToken =
        objectMapper
            .readTree(login.getResponse().getContentAsString())
            .get("data")
            .get("accessToken")
            .asText();
    assertThat(accessToken).isNotBlank();
  }

  @Test
  @Order(2)
  void deleteAccountRejectsWrongPassword() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/auth/account")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(3)
  void deleteAccountPurgesPersonalData() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/auth/account")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"SecurePass123!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "delete-me@example.com",
                                  "password": "SecurePass123!"
                                }
                                """))
        .andExpect(status().isUnauthorized());
  }
}
