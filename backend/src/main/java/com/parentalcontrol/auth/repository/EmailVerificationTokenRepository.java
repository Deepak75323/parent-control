package com.parentalcontrol.auth.repository;

import com.parentalcontrol.auth.domain.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, UUID> {

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      """
            UPDATE EmailVerificationToken t
            SET t.usedAt = :now, t.updatedAt = :now
            WHERE t.userId = :userId AND t.usedAt IS NULL AND t.deletedAt IS NULL
            """)
  void invalidateActiveTokensForUser(UUID userId, Instant now);

  @Modifying
  @Query(
      """
            UPDATE EmailVerificationToken t
            SET t.usedAt = :now, t.updatedAt = :now
            WHERE t.tokenHash = :tokenHash
              AND t.usedAt IS NULL
              AND t.expiresAt > :now
              AND t.deletedAt IS NULL
            """)
  int claimToken(String tokenHash, Instant now);
}
