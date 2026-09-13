package com.parentalcontrol.friend.repository;

import com.parentalcontrol.friend.domain.ParentFriendship;
import com.parentalcontrol.friend.domain.ParentFriendshipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParentFriendshipRepository extends JpaRepository<ParentFriendship, UUID> {

  List<ParentFriendship> findByAddresseeUserIdAndStatusOrderByCreatedAtDesc(
      UUID addresseeUserId, ParentFriendshipStatus status);

  List<ParentFriendship> findByRequesterUserIdAndStatusOrderByCreatedAtDesc(
      UUID requesterUserId, ParentFriendshipStatus status);

  @Query(
      """
      SELECT f FROM ParentFriendship f
      WHERE f.status = :status
        AND f.deletedAt IS NULL
        AND (f.requesterUserId = :userId OR f.addresseeUserId = :userId)
      ORDER BY f.updatedAt DESC
      """)
  List<ParentFriendship> findAcceptedForUser(
      @Param("userId") UUID userId, @Param("status") ParentFriendshipStatus status);

  @Query(
      """
      SELECT f FROM ParentFriendship f
      WHERE f.deletedAt IS NULL
        AND f.status IN :statuses
        AND ((f.requesterUserId = :a AND f.addresseeUserId = :b)
          OR (f.requesterUserId = :b AND f.addresseeUserId = :a))
      """)
  Optional<ParentFriendship> findActiveBetween(
      @Param("a") UUID a,
      @Param("b") UUID b,
      @Param("statuses") List<ParentFriendshipStatus> statuses);
}
