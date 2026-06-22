package com.parentalcontrol.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.billing.repository.SubscriptionRepository;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.BillingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingProperties billingProperties;
    private final ObjectMapper objectMapper;

    public void handle(String payload, String signatureHeader) {
        verifySignature(payload, signatureHeader);

        try {
            JsonNode event = objectMapper.readTree(payload);
            String type = event.path("type").asText();
            JsonNode data = event.path("data").path("object");

            switch (type) {
                case "checkout.session.completed" -> handleCheckoutCompleted(data);
                case "invoice.paid" -> handleInvoicePaid(data);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(data);
                case "customer.subscription.updated" -> handleSubscriptionUpdated(data);
                default -> log.debug("Unhandled Stripe event: {}", type);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid webhook payload");
        }
    }

    private void handleCheckoutCompleted(JsonNode session) {
        String parentProfileId = session.path("client_reference_id").asText(null);
        if (parentProfileId == null || parentProfileId.isBlank()) {
            parentProfileId = session.path("metadata").path("parent_profile_id").asText(null);
        }
        String planId = session.path("metadata").path("plan_id").asText("standard");
        String customerId = session.path("customer").asText();
        String subscriptionId = session.path("subscription").asText();

        if (parentProfileId == null || parentProfileId.isBlank()) {
            log.warn("checkout.session.completed without parent_profile_id");
            return;
        }

        subscriptionService.activateFromProvider(
                UUID.fromString(parentProfileId),
                planId,
                "stripe",
                customerId,
                subscriptionId,
                Instant.now().plusSeconds(30L * 24 * 3600));
    }

    private void handleInvoicePaid(JsonNode invoice) {
        String providerSubscriptionId = invoice.path("subscription").asText();
        long amount = invoice.path("amount_paid").asLong();
        String currency = invoice.path("currency").asText("usd").toUpperCase();
        String paymentId = invoice.path("id").asText();
        long periodEnd = invoice.path("lines").path("data").path(0).path("period").path("end").asLong(0);
        Instant end = periodEnd > 0 ? Instant.ofEpochSecond(periodEnd) : Instant.now().plusSeconds(30L * 24 * 3600);

        subscriptionRepository.findByProviderSubscriptionId(providerSubscriptionId).ifPresent(sub -> {
            subscriptionService.activateFromProvider(
                    sub.getParentProfileId(),
                    sub.getPlanId(),
                    "stripe",
                    sub.getProviderCustomerId(),
                    providerSubscriptionId,
                    end);
            subscriptionService.recordPayment(sub.getId(), amount, currency, "stripe", paymentId, "PAID");
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
            log.warn("Stripe webhook secret not set — skipping signature verification (dev only)");
            return;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing Stripe-Signature header");
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
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid Stripe-Signature format");
        }

        try {
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
            if (!constantTimeEquals(expected, v1)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid Stripe webhook signature");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Webhook verification failed");
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
