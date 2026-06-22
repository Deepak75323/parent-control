package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.billing")
public class BillingProperties {

    private boolean enforcementEnabled = true;

    private int trialDays = 14;

    private String stripeSecretKey = "";

    private String stripeWebhookSecret = "";

    private String stripePriceStandard = "";

    private String stripePriceFamily = "";

    private String checkoutSuccessUrl = "https://parentalcontrol.local/billing/success";

    private String checkoutCancelUrl = "https://parentalcontrol.local/billing/cancel";
}
