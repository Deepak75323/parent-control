package com.parentalcontrol.user.repository;

import com.parentalcontrol.user.domain.ParentProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ParentProfileRepository extends JpaRepository<ParentProfile, UUID> {

  Optional<ParentProfile> findByUser_Id(UUID userId);

  @Query("SELECT p FROM ParentProfile p LEFT JOIN FETCH p.user WHERE p.id = :id")
  Optional<ParentProfile> findByIdWithUser(UUID id);

  @Query("SELECT p.user.id FROM ParentProfile p WHERE p.id = :profileId")
  Optional<UUID> findUserIdByProfileId(UUID profileId);
}
