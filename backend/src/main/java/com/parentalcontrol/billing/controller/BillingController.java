package com.parentalcontrol.billing.controller;

import com.parentalcontrol.billing.dto.CheckoutSessionResponse;
import com.parentalcontrol.billing.dto.PlanResponse;
import com.parentalcontrol.billing.dto.SubscriptionResponse;
import com.parentalcontrol.billing.service.StripeWebhookService;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Subscriptions and checkout")
public class BillingController {

    private final SubscriptionService subscriptionService;
    private final StripeWebhookService stripeWebhookService;

    @GetMapping("/plans")
    @Operation(summary = "List available subscription plans")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<PlanResponse>> plans() {
        return ApiResponse.ok(subscriptionService.listPlans());
    }

    @GetMapping("/subscription")
    @Operation(summary = "Current parent subscription (starts trial if none)")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<SubscriptionResponse> subscription() {
        return ApiResponse.ok(subscriptionService.getCurrentForParent());
    }

    @PostMapping("/checkout")
    @Operation(summary = "Create Stripe checkout session for plan upgrade")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<CheckoutSessionResponse> checkout(@RequestBody Map<String, String> body) {
        String planId = body.getOrDefault("planId", "standard");
        return ApiResponse.ok(subscriptionService.createCheckoutSession(planId));
    }

    @PostMapping("/webhooks/stripe")
    @Operation(summary = "Stripe webhook (public)")
    public ApiResponse<Void> stripeWebhook(
            @RequestBody byte[] payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        stripeWebhookService.handle(new String(payload, java.nio.charset.StandardCharsets.UTF_8), signature);
        return ApiResponse.okMessage("ok");
    }

    @PostMapping("/webhooks/razorpay")
    @Operation(summary = "Razorpay webhook stub (public)")
    public ApiResponse<Void> razorpayWebhook(@RequestBody String payload) {
        // Extend with Razorpay signature verification when enabling India payments
        return ApiResponse.okMessage("razorpay webhook received");
    }
}
