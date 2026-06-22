package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.PolicyViolationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PolicyViolationEventRepository extends JpaRepository<PolicyViolationEvent, UUID> {
}
