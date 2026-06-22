package com.parentalcontrol.billing.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckoutSessionResponse {
    private String checkoutUrl;
    private String planId;
}
