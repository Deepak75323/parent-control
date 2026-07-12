package com.parentalcontrol.billing.dao;

import com.parentalcontrol.billing.domain.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for Subscription. Services depend on this interface; SubscriptionDaoImpl
 * delegates to SubscriptionRepository.
 */
public interface SubscriptionDao {

  Optional<Subscription> findById(UUID id);

  Subscription save(Subscription entity);

  <S extends Subscription> List<S> saveAll(Iterable<S> entities);

  void delete(Subscription entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends Subscription> entities);

  boolean existsById(UUID id);

  long count();

  List<Subscription> findAll();

  void flush();

  Optional<Subscription> findFirstByParentProfileIdOrderByCreatedAtDesc(UUID parentProfileId);

  Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId);
}
