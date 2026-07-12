package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.SignalingMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for SignalingMessage. Services depend on this interface;
 * SignalingMessageDaoImpl delegates to SignalingMessageRepository.
 */
public interface SignalingMessageDao {

  Optional<SignalingMessage> findById(UUID id);

  SignalingMessage save(SignalingMessage entity);

  <S extends SignalingMessage> List<S> saveAll(Iterable<S> entities);

  void delete(SignalingMessage entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends SignalingMessage> entities);

  boolean existsById(UUID id);

  long count();

  List<SignalingMessage> findAll();

  void flush();

  List<SignalingMessage> findTop50ByRoomIdOrderByCreatedAtDesc(String roomId);

  void deleteByRoomId(String roomId);

  int deleteByCreatedAtBefore(Instant cutoff);
}
