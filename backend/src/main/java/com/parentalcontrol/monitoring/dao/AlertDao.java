package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.Alert;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Data-access facade for Alert. Services depend on this interface; AlertDaoImpl delegates to
 * AlertRepository.
 */
public interface AlertDao {

  Optional<Alert> findById(UUID id);

  Alert save(Alert entity);

  <S extends Alert> List<S> saveAll(Iterable<S> entities);

  void delete(Alert entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends Alert> entities);

  boolean existsById(UUID id);

  long count();

  List<Alert> findAll();

  void flush();

  Page<Alert> findByDeviceIdInOrderByCreatedAtDesc(Collection<UUID> deviceIds, Pageable pageable);
}
