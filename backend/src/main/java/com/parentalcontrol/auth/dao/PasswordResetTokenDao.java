package com.parentalcontrol.auth.dao;

import com.parentalcontrol.auth.domain.PasswordResetToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for PasswordResetToken. Services depend on this interface;
 * PasswordResetTokenDaoImpl delegates to PasswordResetTokenRepository.
 */
public interface PasswordResetTokenDao {

  Optional<PasswordResetToken> findById(UUID id);

  PasswordResetToken save(PasswordResetToken entity);

  <S extends PasswordResetToken> List<S> saveAll(Iterable<S> entities);

  void delete(PasswordResetToken entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends PasswordResetToken> entities);

  boolean existsById(UUID id);

  long count();

  List<PasswordResetToken> findAll();

  void flush();

  Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
      String tokenHash, Instant now);

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  void invalidateActiveTokensForUser(UUID userId, Instant now);

  int claimToken(String tokenHash, Instant now);
}
