package com.parentalcontrol.billing.repository;

import com.parentalcontrol.billing.domain.StripeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, String> {}
