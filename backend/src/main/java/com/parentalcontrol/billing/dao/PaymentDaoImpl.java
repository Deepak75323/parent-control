package com.parentalcontrol.billing.dao;

import com.parentalcontrol.billing.domain.Payment;
import com.parentalcontrol.billing.repository.PaymentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentDaoImpl implements PaymentDao {

  private final PaymentRepository repository;

  @Override
  public Optional<Payment> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public Payment save(Payment entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends Payment> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(Payment entity) {
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
  public void deleteAll(Iterable<? extends Payment> entities) {
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
  public List<Payment> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<Payment> findByProviderRef(String providerRef) {
    return repository.findByProviderRef(providerRef);
  }
}
