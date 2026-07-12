package com.parentalcontrol.auth.dao;

import com.parentalcontrol.auth.domain.RefreshToken;
import com.parentalcontrol.auth.repository.RefreshTokenRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenDaoImpl implements RefreshTokenDao {

  private final RefreshTokenRepository repository;

  @Override
  public Optional<RefreshToken> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public RefreshToken save(RefreshToken entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends RefreshToken> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(RefreshToken entity) {
    repository.delete(entity);
  }

  @Override
  public void deleteById(UUID id) {
    repository.deleteById(id);
  }

  @Override
  public void deleteAll() {
    repository.deleteAll();
  }

  @Override
  public void deleteAll(Iterable<? extends RefreshToken> entities) {
    repository.deleteAll(entities);
  }

  @Override
  public boolean existsById(UUID id) {
    return repository.existsById(id);
  }

  @Override
  public long count() {
    return repository.count();
  }

  @Override
  public List<RefreshToken> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash) {
    return repository.findByTokenHashAndRevokedFalse(tokenHash);
  }

  @Override
  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    return repository.findByTokenHash(tokenHash);
  }

  @Override
  public Optional<RefreshToken> findByTokenHashForUpdate(String hash) {
    return repository.findByTokenHashForUpdate(hash);
  }

  @Override
  public void revokeAllByUserId(UUID userId) {
    repository.revokeAllByUserId(userId);
  }
}
