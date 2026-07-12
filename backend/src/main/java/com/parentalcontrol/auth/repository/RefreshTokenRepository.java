package com.parentalcontrol.auth.repository;

import com.parentalcontrol.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Fetches a refresh token by hash and acquires a PostgreSQL row-level exclusive lock ({@code
   * SELECT … FOR UPDATE}) for the duration of the caller's transaction.
   *
   * <p>This is the single correct entry-point for the token-rotation critical section: concurrent
   * requests that arrive with the same token are serialised at the database, so only the first one
   * can find the token in an active (non-revoked) state. All subsequent holders of the same token
   * find it already revoked and are rejected.
   *
   * <p>The {@code jakarta.persistence.lock.timeout} hint translates to PostgreSQL's {@code
   * lock_timeout} (milliseconds). A request that cannot acquire the lock within 3 s receives a
   * {@link jakarta.persistence.LockTimeoutException}.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

  @Modifying
  @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId")
  void revokeAllByUserId(UUID userId);
}
