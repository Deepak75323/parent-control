package com.parentalcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.messaging.ClusterStompMessagingService;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Validates Redis-backed STOMP cluster fan-out (production default for multi-replica). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class StompRedisClusterIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private MockMvc mockMvc;

  @Autowired private ClusterStompMessagingService clusterStompMessagingService;

  @Autowired private ObjectMapper objectMapper;

  private WebSocketStompClient stompClient;
  private static String deviceToken;
  private static UUID deviceId;
  private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_stomp_redis_test");
    registry.add("app.stomp.cluster.mode", () -> "redis");
    registry.add("app.stomp.relay-enabled", () -> "false");
    registry.add("app.hmac.required", () -> "false");
  }

  @BeforeEach
  void setUp() throws Exception {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    provisionDeviceIfNeeded();
  }

  @Test
  void redisClusterFanoutDeliversTopicMessageToSubscriber() throws Exception {
    String topic = "/topic/device/" + deviceId + "/policy-updates";
    var received = new LinkedBlockingQueue<String>();
    CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + deviceToken);

    stompClient.connectAsync(
        "ws://localhost:" + port + "/ws-native",
        new WebSocketHttpHeaders(),
        connectHeaders,
        new StompSessionHandlerAdapter() {
          @Override
          public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            session.subscribe(
                topic,
                new StompFrameHandler() {
                  @Override
                  public Type getPayloadType(StompHeaders headers) {
                    return byte[].class;
                  }

                  @Override
                  public void handleFrame(StompHeaders headers, Object payload) {
                    received.offer(new String((byte[]) payload));
                  }
                });
            sessionFuture.complete(session);
          }

          @Override
          public void handleException(
              StompSession session,
              StompCommand command,
              StompHeaders headers,
              byte[] payload,
              Throwable exception) {
            sessionFuture.completeExceptionally(exception);
          }
        });

    StompSession session = sessionFuture.get(15, TimeUnit.SECONDS);
    Thread.sleep(500);

    clusterStompMessagingService.convertAndSend(
        topic, Map.of("eventType", "POLICY_UPDATED", "policyVersion", "v-redis-cluster"));

    String body = received.poll(10, TimeUnit.SECONDS);
    assertThat(body).isNotNull().contains("v-redis-cluster");

    session.disconnect();
  }

  private void provisionDeviceIfNeeded() throws Exception {
    if (deviceToken != null) {
      return;
    }
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "stomp-redis-%s@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Redis",
                                  "lastName": "Cluster",
                                  "consentAccepted": "true"
                                }
                                """
                        .formatted(RUN_ID)))
        .andExpect(status().isCreated());

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {"email": "stomp-redis-%s@example.com", "password": "SecurePass123!"}
                                """
                            .formatted(RUN_ID)))
            .andExpect(status().isOk())
            .andReturn();
    String parentToken =
        objectMapper
            .readTree(login.getResponse().getContentAsString())
            .get("data")
            .get("accessToken")
            .asText();

    String pairingCode =
        IntegrationTestSupport.createPairingCode(mockMvc, objectMapper, parentToken);

    MvcResult pair =
        mockMvc
            .perform(
                post("/api/v1/device/pair")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "pairingCode": "%s",
                                  "deviceName": "Redis Cluster Device",
                                  "deviceModel": "Pixel",
                                  "osVersion": "14",
                                  "androidId": "stomp-redis-android-%s",
                                  "consentAccepted": "true"
                                }
                                """
                            .formatted(pairingCode, RUN_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode pairBody = objectMapper.readTree(pair.getResponse().getContentAsString()).get("data");
    deviceToken = pairBody.get("deviceToken").asText();
    deviceId = UUID.fromString(pairBody.get("deviceId").asText());
  }
}
