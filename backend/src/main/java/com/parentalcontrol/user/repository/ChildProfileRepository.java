package com.parentalcontrol.user.repository;

import com.parentalcontrol.user.domain.ChildProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildProfileRepository extends JpaRepository<ChildProfile, UUID> {

  List<ChildProfile> findByParentProfileId(UUID parentProfileId);
}
