package com.parentalcontrol.billing.repository;

import com.parentalcontrol.billing.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByProviderRef(String providerRef);
}
