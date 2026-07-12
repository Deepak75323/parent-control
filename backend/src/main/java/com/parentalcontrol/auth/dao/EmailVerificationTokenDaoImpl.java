package com.parentalcontrol.auth.dao;

import com.parentalcontrol.auth.domain.EmailVerificationToken;
import com.parentalcontrol.auth.repository.EmailVerificationTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EmailVerificationTokenDaoImpl implements EmailVerificationTokenDao {

  private final EmailVerificationTokenRepository repository;

  @Override
  public Optional<EmailVerificationToken> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public EmailVerificationToken save(EmailVerificationToken entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends EmailVerificationToken> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(EmailVerificationToken entity) {
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
  public void deleteAll(Iterable<? extends EmailVerificationToken> entities) {
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
  public List<EmailVerificationToken> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
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
