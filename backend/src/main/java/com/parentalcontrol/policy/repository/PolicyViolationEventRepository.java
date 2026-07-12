package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.PolicyViolationEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyViolationEventRepository extends JpaRepository<PolicyViolationEvent, UUID> {}
