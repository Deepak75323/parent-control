package com.parentalcontrol.library.repository;

import com.parentalcontrol.library.domain.LibraryBook;
import com.parentalcontrol.library.domain.LibraryBookStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryBookRepository extends JpaRepository<LibraryBook, UUID> {

  Optional<LibraryBook> findByOwnerUserIdAndContentHash(UUID ownerUserId, String contentHash);

  @Query(
      """
      SELECT b FROM LibraryBook b
      WHERE b.deletedAt IS NULL
        AND b.status = :status
        AND (
          b.ownerUserId = :userId
          OR (b.visibility = com.parentalcontrol.library.domain.LibraryVisibility.SHARED
              AND b.ownerUserId IN :friendIds)
        )
      ORDER BY b.updatedAt DESC
      """)
  List<LibraryBook> findVisibleToUser(
      @Param("userId") UUID userId,
      @Param("friendIds") List<UUID> friendIds,
      @Param("status") LibraryBookStatus status);
}
