package com.parentalcontrol.billing.dao;

import com.parentalcontrol.billing.domain.Subscription;
import com.parentalcontrol.billing.repository.SubscriptionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SubscriptionDaoImpl implements SubscriptionDao {

  private final SubscriptionRepository repository;

  @Override
  public Optional<Subscription> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public Subscription save(Subscription entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends Subscription> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(Subscription entity) {
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
  public void deleteAll(Iterable<? extends Subscription> entities) {
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
  public List<Subscription> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<Subscription> findFirstByParentProfileIdOrderByCreatedAtDesc(
      UUID parentProfileId) {
    return repository.findFirstByParentProfileIdOrderByCreatedAtDesc(parentProfileId);
  }

  @Override
  public Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId) {
    return repository.findByProviderSubscriptionId(providerSubscriptionId);
  }
}
