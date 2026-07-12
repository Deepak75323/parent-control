package com.parentalcontrol.auth.dao;

import com.parentalcontrol.auth.domain.PasswordResetToken;
import com.parentalcontrol.auth.repository.PasswordResetTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenDaoImpl implements PasswordResetTokenDao {

  private final PasswordResetTokenRepository repository;

  @Override
  public Optional<PasswordResetToken> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public PasswordResetToken save(PasswordResetToken entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends PasswordResetToken> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(PasswordResetToken entity) {
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
  public void deleteAll(Iterable<? extends PasswordResetToken> entities) {
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
  public List<PasswordResetToken> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
      String tokenHash, Instant now) {
    return repository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(tokenHash, now);
  }

  @Override
  public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
    return repository.findByTokenHash(tokenHash);
  }

  @Override
  public void invalidateActiveTokensForUser(UUID userId, Instant now) {
    repository.invalidateActiveTokensForUser(userId, now);
  }

  @Override
  public int claimToken(String tokenHash, Instant now) {
    return repository.claimToken(tokenHash, now);
  }
}
