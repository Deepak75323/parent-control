package com.parentalcontrol.billing.dao;

import com.parentalcontrol.billing.domain.StripeWebhookEvent;
import com.parentalcontrol.billing.repository.StripeWebhookEventRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StripeWebhookEventDaoImpl implements StripeWebhookEventDao {

  private final StripeWebhookEventRepository repository;

  @Override
  public Optional<StripeWebhookEvent> findById(String id) {
    return repository.findById(id);
  }

  @Override
  public StripeWebhookEvent save(StripeWebhookEvent entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends StripeWebhookEvent> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(StripeWebhookEvent entity) {
    repository.delete(entity);
  }

  @Override
  public void deleteById(String id) {
    repository.deleteById(id);
  }

  @Override
  public void deleteAll() {
    repository.deleteAll();
  }

  @Override
  public void deleteAll(Iterable<? extends StripeWebhookEvent> entities) {
    repository.deleteAll(entities);
  }

  @Override
  public boolean existsById(String id) {
    return repository.existsById(id);
  }

  @Override
  public long count() {
    return repository.count();
  }

  @Override
  public List<StripeWebhookEvent> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }
}
