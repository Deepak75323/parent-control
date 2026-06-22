package com.parentalcontrol.billing.repository;

import com.parentalcontrol.billing.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findFirstByParentProfileIdOrderByCreatedAtDesc(UUID parentProfileId);

    Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId);
}
