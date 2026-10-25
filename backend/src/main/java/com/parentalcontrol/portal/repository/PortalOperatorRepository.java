package com.parentalcontrol.portal.repository;

import com.parentalcontrol.portal.domain.PortalOperator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalOperatorRepository extends JpaRepository<PortalOperator, UUID> {

  List<PortalOperator> findAllByOrderByGrantedAtAsc();

  Optional<PortalOperator> findByFamilyHandleIgnoreCase(String familyHandle);

  boolean existsByUserId(UUID userId);
}
