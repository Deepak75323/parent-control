package com.parentalcontrol.user.repository;

import com.parentalcontrol.user.domain.ChildProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChildProfileRepository extends JpaRepository<ChildProfile, UUID> {

    List<ChildProfile> findByParentProfileId(UUID parentProfileId);
}
