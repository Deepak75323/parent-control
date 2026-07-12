package com.parentalcontrol.billing.dao;

import com.parentalcontrol.billing.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for Payment. Services depend on this interface; PaymentDaoImpl delegates to
 * PaymentRepository.
 */
public interface PaymentDao {

  Optional<Payment> findById(UUID id);

  Payment save(Payment entity);

  <S extends Payment> List<S> saveAll(Iterable<S> entities);

  void delete(Payment entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends Payment> entities);

  boolean existsById(UUID id);

  long count();

  List<Payment> findAll();

  void flush();

  Optional<Payment> findByProviderRef(String providerRef);
}
