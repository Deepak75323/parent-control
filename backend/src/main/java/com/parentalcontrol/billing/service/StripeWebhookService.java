package com.parentalcontrol.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.billing.dao.StripeWebhookEventDao;
import com.parentalcontrol.billing.dao.SubscriptionDao;
import com.parentalcontrol.billing.domain.StripeWebhookEvent;
import com.parentalcontrol.billing.domain.SubscriptionPlan;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.constant.TimeoutConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.BillingProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

  private static final long WEBHOOK_TOLERANCE_SECONDS =
      TimeoutConstants.STRIPE_WEBHOOK_TOLERANCE_SECONDS;

  private final SubscriptionService subscriptionService;
  private final SubscriptionDao subscriptionRepository;
  private final StripeWebhookEventDao stripeWebhookEventRepository;
  private final StripeCheckoutService stripeCheckoutService;
  private final BillingProperties billingProperties;
  private final ObjectMapper objectMapper;

  @Transactional
  public void handle(String payload, String signatureHeader) {
    verifySignature(payload, signatureHeader);

    try {
      JsonNode event = objectMapper.readTree(payload);
      String eventId = event.path("id").asText(null);
      if (eventId == null || eventId.isBlank()) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR, MessageConstants.MISSING_STRIPE_EVENT_ID);
      }
      if (stripeWebhookEventRepository.existsById(eventId)) {
        log.debug("Ignoring duplicate Stripe event id={}", eventId);
        return;
      }

      String type = event.path("type").asText();
      JsonNode data = event.path("data").path("object");

      switch (type) {
        case "checkout.session.completed" -> handleCheckoutCompleted(data);
        case "invoice.paid" -> handleInvoicePaid(data);
        case "customer.subscription.deleted" -> handleSubscriptionDeleted(data);
        case "customer.subscription.updated" -> handleSubscriptionUpdated(data);
        default -> log.debug("Unhandled Stripe event: {}", type);
      }

      stripeWebhookEventRepository.save(
          StripeWebhookEvent.builder()
              .eventId(eventId)
              .eventType(type)
              .processedAt(Instant.now())
              .build());
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.INVALID_WEBHOOK_PAYLOAD);
    }
  }

  private void handleCheckoutCompleted(JsonNode session) {
    if (!"paid".equals(session.path("payment_status").asText())) {
      log.warn(
          "Ignoring checkout.session.completed with payment_status={}",
          session.path("payment_status").asText());
      return;
    }
    if (!"complete".equals(session.path("status").asText())) {
      log.warn(
          "Ignoring checkout.session.completed with status={}", session.path("status").asText());
      return;
    }
    long amountTotal = session.path("amount_total").asLong(0);
    if (amountTotal <= 0) {
      log.warn("Ignoring checkout.session.completed with zero amount_total");
      return;
    }

    String parentProfileId = session.path("client_reference_id").asText(null);
    if (parentProfileId == null || parentProfileId.isBlank()) {
      parentProfileId = session.path("metadata").path("parent_profile_id").asText(null);
    }
    String customerId = session.path("customer").asText();
    String subscriptionId = session.path("subscription").asText();

    if (parentProfileId == null || parentProfileId.isBlank()) {
      log.warn("checkout.session.completed without parent_profile_id");
      return;
    }

    String planId = resolvePlanFromSession(session);
    if (planId == null) {
      log.warn("checkout.session.completed could not verify plan from Stripe session");
      return;
    }

    long periodEnd = session.path("expires_at").asLong(0);
    Instant end =
        periodEnd > 0
            ? Instant.ofEpochSecond(periodEnd)
            : Instant.now().plusSeconds(30L * 24 * 3600);

    subscriptionService.activateFromProvider(
        UUID.fromString(parentProfileId), planId, "stripe", customerId, subscriptionId, end);
  }

  private String resolvePlanFromSession(JsonNode session) {
    String planFromPrices = resolvePlanFromLineItems(session.path("line_items").path("data"));
    if (planFromPrices != null) {
      return planFromPrices;
    }

    String sessionId = session.path("id").asText(null);
    if (sessionId != null && !sessionId.isBlank()) {
      JsonNode expanded = stripeCheckoutService.retrieveCheckoutSession(sessionId);
      if (expanded != null) {
        return resolvePlanFromLineItems(expanded.path("line_items").path("data"));
      }
    }

    return null;
  }

  private String resolvePlanFromLineItems(JsonNode items) {
    if (!items.isArray() || items.isEmpty()) {
      return null;
    }
    String standardPrice = billingProperties.getStripePriceStandard();
    String familyPrice = billingProperties.getStripePriceFamily();

    for (JsonNode item : items) {
      String priceId = item.path("price").path("id").asText(null);
      if (priceId != null && priceId.equals(familyPrice)) {
        return SubscriptionPlan.FAMILY.getPlanId();
      }
      if (priceId != null && priceId.equals(standardPrice)) {
        return SubscriptionPlan.STANDARD.getPlanId();
      }
    }
    return null;
  }

  private void handleInvoicePaid(JsonNode invoice) {
    String providerSubscriptionId = invoice.path("subscription").asText();
    long amount = invoice.path("amount_paid").asLong();
    if (amount <= 0) {
      log.warn("Ignoring invoice.paid with zero amount_paid");
      return;
    }
    String currency = invoice.path("currency").asText("usd").toUpperCase();
    String paymentId = invoice.path("id").asText();
    long periodEnd =
        invoice.path("lines").path("data").path(0).path("period").path("end").asLong(0);
    Instant end =
        periodEnd > 0
            ? Instant.ofEpochSecond(periodEnd)
            : Instant.now().plusSeconds(30L * 24 * 3600);

    subscriptionRepository
        .findByProviderSubscriptionId(providerSubscriptionId)
        .ifPresent(
            sub -> {
              subscriptionService.activateFromProvider(
                  sub.getParentProfileId(),
                  sub.getPlanId(),
                  "stripe",
                  sub.getProviderCustomerId(),
                  providerSubscriptionId,
                  end);
              subscriptionService.recordPayment(
                  sub.getId(), amount, currency, "stripe", paymentId, "PAID");
            });
  }

  private void handleSubscriptionDeleted(JsonNode sub) {
    subscriptionService.markCancelled(sub.path("id").asText());
  }

  private void handleSubscriptionUpdated(JsonNode sub) {
    String status = sub.path("status").asText();
    if ("past_due".equals(status) || "unpaid".equals(status)) {
      long periodEnd = sub.path("current_period_end").asLong(0);
      Instant end = periodEnd > 0 ? Instant.ofEpochSecond(periodEnd) : Instant.now();
      subscriptionService.markPastDue(sub.path("id").asText(), end);
    }
  }

  private void verifySignature(String payload, String signatureHeader) {
    String secret = billingProperties.getStripeWebhookSecret();
    if (secret == null || secret.isBlank()) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.STRIPE_WEBHOOK_VERIFICATION_IS_NOT_CONFIGURED);
    }
    if (signatureHeader == null || signatureHeader.isBlank()) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.MISSING_STRIPE_SIGNATURE_HEADER);
    }

    String timestamp = null;
    String v1 = null;
    for (String part : signatureHeader.split(",")) {
      String[] kv = part.trim().split("=", 2);
      if (kv.length == 2) {
        if ("t".equals(kv[0])) {
          timestamp = kv[1];
        } else if ("v1".equals(kv[0])) {
          v1 = kv[1];
        }
      }
    }
    if (timestamp == null || v1 == null) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_STRIPE_SIGNATURE_FORMAT);
    }

    long eventTime;
    try {
      eventTime = Long.parseLong(timestamp);
    } catch (NumberFormatException ex) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_STRIPE_SIGNATURE_TIMESTAMP);
    }
    long now = Instant.now().getEpochSecond();
    if (Math.abs(now - eventTime) > WEBHOOK_TOLERANCE_SECONDS) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.WEBHOOK_TIMESTAMP_TOO_OLD);
    }

    try {
      String signedPayload = timestamp + "." + payload;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String expected =
          HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
      if (!constantTimeEquals(expected, v1)) {
        throw new BusinessException(
            ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_STRIPE_WEBHOOK_SIGNATURE);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.WEBHOOK_VERIFICATION_FAILED);
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a.length() != b.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }
}
