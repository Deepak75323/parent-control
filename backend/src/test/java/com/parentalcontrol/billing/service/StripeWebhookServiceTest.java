package com.parentalcontrol.billing.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.billing.dao.StripeWebhookEventDao;
import com.parentalcontrol.billing.dao.SubscriptionDao;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.config.BillingProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

  private static final String WEBHOOK_SECRET = "whsec_test_secret_key_32bytes!!";

  @Mock private SubscriptionService subscriptionService;
  @Mock private SubscriptionDao subscriptionRepository;
  @Mock private StripeWebhookEventDao stripeWebhookEventRepository;
  @Mock private StripeCheckoutService stripeCheckoutService;

  private StripeWebhookService webhookService;
  private BillingProperties billingProperties;

  @BeforeEach
  void setUp() {
    billingProperties = new BillingProperties();
    billingProperties.setStripeWebhookSecret(WEBHOOK_SECRET);
    billingProperties.setStripePriceStandard("price_standard");
    billingProperties.setStripePriceFamily("price_family");
    webhookService =
        new StripeWebhookService(
            subscriptionService,
            subscriptionRepository,
            stripeWebhookEventRepository,
            stripeCheckoutService,
            billingProperties,
            new ObjectMapper());
  }

  @Test
  void rejectsWhenWebhookSecretMissing() {
    billingProperties.setStripeWebhookSecret("");
    assertThrows(BusinessException.class, () -> webhookService.handle("{}", "t=1,v1=abc"));
  }

  @Test
  void rejectsStaleTimestamp() {
    long stale = Instant.now().getEpochSecond() - 600;
    String payload = eventPayload("evt_stale", "invoice.paid");
    String sig = sign(payload, stale);
    assertThrows(BusinessException.class, () -> webhookService.handle(payload, sig));
  }

  @Test
  void acceptsValidSignatureWithinTolerance() {
    when(stripeWebhookEventRepository.existsById(anyString())).thenReturn(false);
    long now = Instant.now().getEpochSecond();
    String payload = eventPayload("evt_ok", "customer.subscription.deleted");
    String sig = sign(payload, now);
    assertDoesNotThrow(() -> webhookService.handle(payload, sig));
  }

  private static String eventPayload(String eventId, String type) {
    return "{\"id\":\""
        + eventId
        + "\",\"type\":\""
        + type
        + "\",\"data\":{\"object\":{\"id\":\"sub_1\"}}}";
  }

  private String sign(String payload, long timestamp) {
    try {
      String signedPayload = timestamp + "." + payload;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String v1 =
          HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
      return "t=" + timestamp + ",v1=" + v1;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
