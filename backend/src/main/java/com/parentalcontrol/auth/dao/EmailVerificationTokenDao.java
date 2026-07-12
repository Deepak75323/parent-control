package com.parentalcontrol.auth.dao;

import com.parentalcontrol.auth.domain.EmailVerificationToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for EmailVerificationToken. Services depend on this interface;
 * EmailVerificationTokenDaoImpl delegates to EmailVerificationTokenRepository.
 */
public interface EmailVerificationTokenDao {

  Optional<EmailVerificationToken> findById(UUID id);

  EmailVerificationToken save(EmailVerificationToken entity);

  <S extends EmailVerificationToken> List<S> saveAll(Iterable<S> entities);

  void delete(EmailVerificationToken entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends EmailVerificationToken> entities);

  boolean existsById(UUID id);

  long count();

  List<EmailVerificationToken> findAll();

  void flush();

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  void invalidateActiveTokensForUser(UUID userId, Instant now);

  int claimToken(String tokenHash, Instant now);
}
