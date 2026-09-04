package com.parentalcontrol;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.mock.web.MockMultipartFile;
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
    registry.add(
        "app.chat.attachments.storage-path",
        () -> System.getProperty("java.io.tmpdir") + "/pcp-chat-test-attachments");
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
        .andExpect(jsonPath("$.data[0].body").value("Hello parents!"))
        .andExpect(jsonPath("$.data[0].ownMessage").value(false));

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].ownMessage").value(true));
  }

  @Test
  @Order(6)
  void editMessagePermissions() throws Exception {
    MvcResult sent =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Original text\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String messageId = readData(sent).get("id").asText();

    mockMvc
        .perform(
            patch("/api/v1/chat/rooms/" + roomId + "/messages/" + messageId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Hacked\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            patch("/api/v1/chat/rooms/" + roomId + "/messages/" + messageId)
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Updated text\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.body").value("Updated text"))
        .andExpect(jsonPath("$.data.editedAt").exists());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].body").value("Updated text"));
  }

  @Test
  @Order(7)
  void deleteMessagePermissions() throws Exception {
    MvcResult sent =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Delete me\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String ownMessageId = readData(sent).get("id").asText();

    mockMvc
        .perform(
            delete("/api/v1/chat/rooms/" + roomId + "/messages/" + ownMessageId)
                .param("scope", "FOR_EVERYONE")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    MvcResult other =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Hide for me only\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String otherMessageId = readData(other).get("id").asText();

    mockMvc
        .perform(
            delete("/api/v1/chat/rooms/" + roomId + "/messages/" + otherMessageId)
                .param("scope", "FOR_EVERYONE")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            delete("/api/v1/chat/rooms/" + roomId + "/messages/" + otherMessageId)
                .param("scope", "FOR_ME")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].body").value("Hide for me only"));
  }

  @Test
  @Order(9)
  void sendImageMessage() throws Exception {
    byte[] pngHeader =
        new byte[] {
          (byte) 0x89,
          0x50,
          0x4E,
          0x47,
          0x0D,
          0x0A,
          0x1A,
          0x0A,
          0,
          0,
          0,
          0x0D,
          0x49,
          0x48,
          0x44,
          0x52
        };
    MockMultipartFile file =
        new MockMultipartFile("file", "test.png", "image/png", pngHeader);

    MvcResult upload =
        mockMvc
            .perform(
                multipart("/api/v1/chat/rooms/" + roomId + "/messages/media")
                    .file(file)
                    .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.messageType").value("IMAGE"))
            .andExpect(jsonPath("$.data.attachmentViewOnce").value(true))
            .andExpect(jsonPath("$.data.ownMessage").value(true))
            .andExpect(jsonPath("$.data.attachmentUrl").doesNotExist())
            .andReturn();

    String messageId = readData(upload).get("id").asText();

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages/" + messageId + "/attachment")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages/" + messageId + "/attachment")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/messages/" + messageId + "/attachment/view")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/messages/" + messageId + "/attachment/view")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].attachmentConsumed").value(true))
        .andExpect(jsonPath("$.data[0].canViewAttachment").value(false));
  }

  @Test
  @Order(10)
  void ownerClearsHistory() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Before personal clear\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/clear-chat")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].body").value("Before personal clear"));

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/clear-history")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/clear-history")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(roomId));
  }

  @Test
  @Order(11)
  void memberLeavesRoom() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/leave")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/leave")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(roomId));
  }

  @Test
  @Order(75)
  void readMarksEarlierMessagesWithoutExplicitDelivered() throws Exception {
    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"First in batch\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String firstId = readData(first).get("id").asText();

    MvcResult second =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Second in batch\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String secondId = readData(second).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/read")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastMessageId\":\"" + secondId + "\"}"))
        .andExpect(status().isOk());

    // Auto-delete-when-everyone-read: both messages gone for sender and receiver.
    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id=='" + firstId + "')]").isEmpty())
        .andExpect(jsonPath("$.data[?(@.id=='" + secondId + "')]").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id=='" + firstId + "')]").isEmpty())
        .andExpect(jsonPath("$.data[?(@.id=='" + secondId + "')]").isEmpty());
  }

  @Test
  @Order(8)
  void readReceiptFlow() throws Exception {
    MvcResult sent =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Receipt test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.receiptStatus").value("SENT"))
            .andReturn();
    String messageId = readData(sent).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/delivered")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageIds\":[\"" + messageId + "\"]}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].receiptStatus").value("DELIVERED"));

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/read")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastMessageId\":\"" + messageId + "\"}"))
        .andExpect(status().isOk());

    // Fully read by the only other member → auto-deleted for everyone.
    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id=='" + messageId + "')]").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id=='" + messageId + "')]").isEmpty());

    mockMvc
        .perform(
            post("/api/v1/parent/read-receipts")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));
  }

  @Test
  @Order(73)
  void markUnreadFromAnchorMessage() throws Exception {
    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Unread line one\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String firstId = readData(first).get("id").asText();

    MvcResult second =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Unread line two\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String secondId = readData(second).get("id").asText();

    MvcResult third =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Unread line three\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String thirdId = readData(third).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/chat/rooms/" + roomId + "/read")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastMessageId\":\"" + thirdId + "\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id=='" + firstId + "')].unread").value(false))
        .andExpect(jsonPath("$.data[?(@.id=='" + secondId + "')].unread").value(false))
        .andExpect(jsonPath("$.data[?(@.id=='" + thirdId + "')].unread").value(false));

    mockMvc
        .perform(
            post(
                    "/api/v1/chat/rooms/"
                        + roomId
                        + "/messages/"
                        + secondId
                        + "/mark-unread")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id=='" + firstId + "')].unread").value(false))
        .andExpect(jsonPath("$.data[?(@.id=='" + secondId + "')].unread").value(true))
        .andExpect(jsonPath("$.data[?(@.id=='" + thirdId + "')].unread").value(true));

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id=='" + secondId + "')].receiptStatus").value("READ"))
        .andExpect(jsonPath("$.data[?(@.id=='" + thirdId + "')].receiptStatus").value("READ"));
  }

  @Test
  @Order(74)
  void messageEmojiReactionsToggleLikeWhatsApp() throws Exception {
    MvcResult sent =
        mockMvc
            .perform(
                post("/api/v1/chat/rooms/" + roomId + "/messages")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"React to me\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String messageId = readData(sent).get("id").asText();

    mockMvc
        .perform(
            put("/api/v1/chat/rooms/" + roomId + "/messages/" + messageId + "/reactions")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"👍\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].emoji").value("👍"))
        .andExpect(jsonPath("$.data[0].count").value(1))
        .andExpect(jsonPath("$.data[0].reactedByMe").value(true));

    mockMvc
        .perform(
            get("/api/v1/chat/rooms/" + roomId + "/messages")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id=='" + messageId + "')].reactions[0].emoji").value("👍"))
        .andExpect(jsonPath("$.data[?(@.id=='" + messageId + "')].reactions[0].count").value(1))
        .andExpect(jsonPath("$.data[?(@.id=='" + messageId + "')].reactions[0].reactedByMe").value(false));

    mockMvc
        .perform(
            put("/api/v1/chat/rooms/" + roomId + "/messages/" + messageId + "/reactions")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"👍\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  @Order(12)
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
