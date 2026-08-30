package com.parentalcontrol;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class ChatIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_chat_test");
    registry.add("app.hmac.required", () -> "false");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private static String tokenA;
  private static String tokenB;
  private static String roomId;
  private static String inviteId;

  @Test
  @Order(1)
  void registerParents() throws Exception {
    tokenA = registerAndLogin("chat-a@local.test", "SecurePass123!");
    tokenB = registerAndLogin("chat-b@local.test", "SecurePass123!");
  }

  @Test
  @Order(2)
  void setFamilyHandles() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/parent/family-handle")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyHandle\":\"chat-parent-a\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/parent/family-handle")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyHandle\":\"chat-parent-b\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @Order(3)
  void createRoomAndInviteByHandle() throws Exception {
    MvcResult create =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Family Chat\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Family Chat"))
            .andExpect(jsonPath("$.data.owner").value(true))
            .andReturn();

    roomId = readData(create).get("id").asText();

    MvcResult invite =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/invites")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"identifier\":\"@chat-parent-b\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn();

    inviteId = readData(invite).get("id").asText();
  }

  @Test
  @Order(4)
  void inviteeSeesPendingInvite() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/chat/invites/pending").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].roomName").value("Family Chat"));
  }

  @Test
  @Order(5)
  void acceptInviteAndSendMessage() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/chat/invites/" + inviteId + "/respond")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

    mockMvc
        .perform(
            get("/api/v1/chat/rooms").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("Family Chat"));

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Hello parents!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.body").value("Hello parents!"));

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].body").value("Hello parents!"));
  }

  @Test
  @Order(6)
  void ownerDeletesRoom() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/chat/rooms/" + roomId).header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            delete("/api/v1/chat/rooms/" + roomId).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  private JsonNode readData(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
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
                      "firstName": "Chat",
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
