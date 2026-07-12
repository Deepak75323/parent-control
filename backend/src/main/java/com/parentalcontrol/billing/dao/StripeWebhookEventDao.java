package com.parentalcontrol.billing.dao;

import com.parentalcontrol.billing.domain.StripeWebhookEvent;
import java.util.List;
import java.util.Optional;

/**
 * Data-access facade for StripeWebhookEvent. Services depend on this interface;
 * StripeWebhookEventDaoImpl delegates to StripeWebhookEventRepository.
 */
public interface StripeWebhookEventDao {

  Optional<StripeWebhookEvent> findById(String id);

  StripeWebhookEvent save(StripeWebhookEvent entity);

  <S extends StripeWebhookEvent> List<S> saveAll(Iterable<S> entities);

  void delete(StripeWebhookEvent entity);

  void deleteById(String id);

  void deleteAll();

  void deleteAll(Iterable<? extends StripeWebhookEvent> entities);

  boolean existsById(String id);

  long count();

  List<StripeWebhookEvent> findAll();

  void flush();
}
