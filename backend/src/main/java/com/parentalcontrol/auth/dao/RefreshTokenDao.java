package com.parentalcontrol.auth.dao;

import com.parentalcontrol.auth.domain.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for RefreshToken. Services depend on this interface; RefreshTokenDaoImpl
 * delegates to RefreshTokenRepository.
 */
public interface RefreshTokenDao {

  Optional<RefreshToken> findById(UUID id);

  RefreshToken save(RefreshToken entity);

  <S extends RefreshToken> List<S> saveAll(Iterable<S> entities);

  void delete(RefreshToken entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends RefreshToken> entities);

  boolean existsById(UUID id);

  long count();

  List<RefreshToken> findAll();

  void flush();

  Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  Optional<RefreshToken> findByTokenHashForUpdate(String hash);

  void revokeAllByUserId(UUID userId);
}
