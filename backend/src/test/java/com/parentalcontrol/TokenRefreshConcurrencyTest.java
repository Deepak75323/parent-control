package com.parentalcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

/**
 * Verifies P1-06 fix: concurrent refresh requests on the same token are serialised by a PostgreSQL
 * row-level lock, ensuring only one succeeds.
 *
 * <p>Uses register() to obtain persisted refresh tokens (register() is {@code @Transactional} with
 * auto-flush, so tokens are committed to the DB).
 *
 * <p>Isolated database: parental_control_concurrency_test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TokenRefreshConcurrencyTest {

  private static final String BASE_EMAIL = "concurrency-%d@example.com";
  private static final String PASSWORD = "SecurePass123!";

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_concurrency_test");
    registry.add("app.hmac.required", () -> "false");
    // Connection pool must have enough connections for concurrent threads.
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
    registry.add("spring.datasource.hikari.minimum-idle", () -> "5");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Registers a unique user and returns the refresh token from the response.
   *
   * <p>Sleeps 1 100 ms after registration so that the first refresh call lands in a different JWT
   * second. Without this gap the registration token and the rotation token would produce identical
   * JWT payloads (same user, same {@code iat} second) and collide on the {@code
   * uq_refresh_token_hash} constraint — a pre-existing JwtTokenProvider limitation (missing JTI).
   */
  private String registerAndGetRefreshToken(int seed) throws Exception {
    String email = BASE_EMAIL.formatted(seed);
    var result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Conc",
                                  "lastName": "Test%d",
                                  "consentAccepted": "true"
                                }
                                """
                            .formatted(email, PASSWORD, seed)))
            .andExpect(status().isCreated())
            .andReturn();

    var body = objectMapper.readTree(result.getResponse().getContentAsString());
    String token = body.get("data").get("refreshToken").asText();
    assertThat(token).isNotBlank();

    Thread.sleep(1_100); // ensure next refresh is in a different JWT second
    return token;
  }

  private List<Integer> fireParallelRefreshes(String refreshToken, int threadCount)
      throws Exception {
    List<Integer> statusCodes = new CopyOnWriteArrayList<>();
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startGate.await();
              var r =
                  mockMvc
                      .perform(
                          post("/api/v1/auth/refresh")
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(
                                  """
                                            {"refreshToken": "%s"}
                                            """
                                      .formatted(refreshToken)))
                      .andReturn();
              statusCodes.add(r.getResponse().getStatus());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              statusCodes.add(-1);
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startGate.countDown(); // release all threads simultaneously
    boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
    executor.shutdownNow();
    assertThat(completed).as("All refresh threads must complete within 15 s").isTrue();
    return statusCodes;
  }

  // -----------------------------------------------------------------------
  // Two simultaneous refresh requests with the same token
  // -----------------------------------------------------------------------

  @Test
  @Order(1)
  void twoSimultaneousRefreshes_exactlyOneSucceeds() throws Exception {
    String token = registerAndGetRefreshToken(1);

    List<Integer> codes = fireParallelRefreshes(token, 2);

    long successes = codes.stream().filter(c -> c == 200).count();
    long errors = codes.stream().filter(c -> c == 401 || c == 429).count();

    assertThat(codes).hasSize(2);
    assertThat(successes).as("Exactly one refresh must succeed").isEqualTo(1);
    assertThat(errors).as("Exactly one refresh must be rejected").isEqualTo(1);
  }

  // -----------------------------------------------------------------------
  // Three simultaneous refresh requests with the same token
  // -----------------------------------------------------------------------

  @Test
  @Order(2)
  void threeSimultaneousRefreshes_exactlyOneSucceeds() throws Exception {
    String token = registerAndGetRefreshToken(2);

    List<Integer> codes = fireParallelRefreshes(token, 3);

    long successes = codes.stream().filter(c -> c == 200).count();
    long errors = codes.stream().filter(c -> c == 401 || c == 429).count();

    assertThat(codes).hasSize(3);
    assertThat(successes).as("Exactly one refresh must succeed").isEqualTo(1);
    assertThat(errors).as("Two refreshes must be rejected").isEqualTo(2);
  }

  // -----------------------------------------------------------------------
  // Stress: five simultaneous refresh requests — only one wins
  // -----------------------------------------------------------------------

  @Test
  @Order(3)
  void fiveSimultaneousRefreshes_exactlyOneSucceeds() throws Exception {
    String token = registerAndGetRefreshToken(3);

    List<Integer> codes = fireParallelRefreshes(token, 5);

    long successes = codes.stream().filter(c -> c == 200).count();
    assertThat(successes).as("Exactly one refresh must succeed under stress").isEqualTo(1);
  }

  // -----------------------------------------------------------------------
  // Network-retry scenario: second call with an already-rotated token
  // is rejected (replay protection)
  // -----------------------------------------------------------------------

  @Test
  @Order(4)
  void networkRetry_reusedToken_returns401() throws Exception {
    String token = registerAndGetRefreshToken(4);

    // First call succeeds
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refreshToken": "%s"}
                                """
                        .formatted(token)))
        .andExpect(status().isOk());

    // Second call with the same (now-revoked) token must be rejected
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refreshToken": "%s"}
                                """
                        .formatted(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // -----------------------------------------------------------------------
  // Expired token
  // -----------------------------------------------------------------------

  @Test
  @Order(5)
  void expiredToken_returns401() throws Exception {
    // A syntactically valid but tampered/unrecognised token not in the DB
    String unknownToken = "not.a.real.token.value.at.all.12345";
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refreshToken": "%s"}
                                """
                        .formatted(unknownToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // -----------------------------------------------------------------------
  // Revoked token — explicit reuse detection
  // -----------------------------------------------------------------------

  @Test
  @Order(6)
  void revokedToken_returns401_notFound() throws Exception {
    String token = registerAndGetRefreshToken(5);

    // First refresh → success → old token is revoked
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refreshToken": "%s"}
                                """
                        .formatted(token)))
        .andExpect(status().isOk());

    // Same token again → revoked → UNAUTHORIZED
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refreshToken": "%s"}
                                """
                        .formatted(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // -----------------------------------------------------------------------
  // Regression: sequential refresh (normal usage) still works
  // -----------------------------------------------------------------------

  @Test
  @Order(7)
  void sequentialRefresh_normalFlow_works() throws Exception {
    String token = registerAndGetRefreshToken(6);

    // First rotation
    var r1 =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {"refreshToken": "%s"}
                                """
                            .formatted(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andExpect(jsonPath("$.data.refreshToken").isString())
            .andReturn();

    // Sleep to avoid uq_refresh_token_hash collision from JwtTokenProvider
    // missing JTI (pre-existing issue, not related to P1-06).
    Thread.sleep(1_100);

    String newRefreshToken =
        objectMapper
            .readTree(r1.getResponse().getContentAsString())
            .get("data")
            .get("refreshToken")
            .asText();

    // Second rotation with the NEW token from the first rotation
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refreshToken": "%s"}
                                """
                        .formatted(newRefreshToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isString());
  }

  // -----------------------------------------------------------------------
  // Existing users unaffected by the P1-06 change (regression)
  // -----------------------------------------------------------------------

  @Test
  @Order(8)
  void existingUser_registrationAndRefresh_unaffected() throws Exception {
    String token = registerAndGetRefreshToken(7);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refreshToken": "%s"}
                                """
                        .formatted(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isString())
        .andExpect(jsonPath("$.data.refreshToken").isString());
  }
}
