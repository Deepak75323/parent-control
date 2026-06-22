package com.parentalcontrol.billing.service;

import com.parentalcontrol.billing.domain.SubscriptionPlan;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.BillingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeCheckoutService {

    private final BillingProperties billingProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String createCheckoutSession(UUID parentProfileId, SubscriptionPlan plan) {
        String secret = billingProperties.getStripeSecretKey();
        if (secret == null || secret.isBlank()) {
            log.warn("Stripe not configured — returning mock checkout URL for dev");
            return billingProperties.getCheckoutSuccessUrl()
                    + "?mock=1&plan=" + plan.getPlanId()
                    + "&parentProfileId=" + parentProfileId;
        }

        String priceId = plan == SubscriptionPlan.FAMILY
                ? billingProperties.getStripePriceFamily()
                : billingProperties.getStripePriceStandard();

        if (priceId == null || priceId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Stripe price ID not configured for plan");
        }

        try {
            String body = formBody(
                    "mode", "subscription",
                    "success_url", billingProperties.getCheckoutSuccessUrl(),
                    "cancel_url", billingProperties.getCheckoutCancelUrl(),
                    "client_reference_id", parentProfileId.toString(),
                    "line_items[0][price]", priceId,
                    "line_items[0][quantity]", "1",
                    "metadata[plan_id]", plan.getPlanId(),
                    "metadata[parent_profile_id]", parentProfileId.toString()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                    .header("Authorization", "Bearer " + secret)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("Stripe checkout failed: {}", response.body());
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Stripe checkout session failed");
            }
            JsonNode json = objectMapper.readTree(response.body());
            return json.path("url").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Stripe checkout error: " + e.getMessage());
        }
    }

    private static String formBody(String... pairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
