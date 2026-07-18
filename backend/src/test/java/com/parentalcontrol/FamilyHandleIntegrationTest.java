package com.parentalcontrol;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FamilyHandleIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_family_handle_test");
    registry.add("app.hmac.required", () -> "false");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private static String accessTokenA;
  private static String accessTokenB;

  @Test
  @Order(1)
  void registerParents() throws Exception {
    accessTokenA = registerAndLogin("handle-a@local.test", "SecurePass123!");
    accessTokenB = registerAndLogin("handle-b@local.test", "SecurePass123!");
  }

  @Test
  @Order(2)
  void statusBeforeConfigured() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/parent/family-handle/status")
                .header("Authorization", "Bearer " + accessTokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.configured").value(false));
  }

  @Test
  @Order(3)
  void setHandle() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/parent/family-handle")
                .header("Authorization", "Bearer " + accessTokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyHandle\":\"@Mom.Sharma\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.configured").value(true))
        .andExpect(jsonPath("$.data.familyHandle").value("mom.sharma"));
  }

  @Test
  @Order(4)
  void rejectDuplicateHandle() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/parent/family-handle")
                .header("Authorization", "Bearer " + accessTokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyHandle\":\"mom.sharma\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @Order(5)
  void onboardingRequiresHandleWhenUnpaired() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/parent/child-login-password")
                .header("Authorization", "Bearer " + accessTokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"childLoginPassword\":\"FamilyPass123\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/onboarding/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "childLoginPassword": "FamilyPass123",
                      "androidId": "unpaired-device-001"
                    }
                    """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/onboarding/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "childLoginPassword": "FamilyPass123",
                      "familyHandle": "mom.sharma",
                      "androidId": "unpaired-device-001"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authenticated").value(true));
  }

  @Test
  @Order(6)
  void changeHandleWhenAvailable() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/parent/family-handle")
                .header("Authorization", "Bearer " + accessTokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyHandle\":\"fam-mom7\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.familyHandle").value("fam-mom7"));
  }

  private String registerAndLogin(String email, String password) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "password": "%s",
                      "firstName": "Handle",
                      "lastName": "Parent",
                      "consentAccepted": "true"
                    }
                    """
                        .formatted(email, password)))
        .andExpect(status().isCreated());

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """
                            .formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(login.getResponse().getContentAsString())
        .get("data")
        .get("accessToken")
        .asText();
  }
}
