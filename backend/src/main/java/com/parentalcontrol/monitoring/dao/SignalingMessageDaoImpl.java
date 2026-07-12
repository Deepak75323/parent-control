package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.SignalingMessage;
import com.parentalcontrol.monitoring.repository.SignalingMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SignalingMessageDaoImpl implements SignalingMessageDao {

  private final SignalingMessageRepository repository;

  @Override
  public Optional<SignalingMessage> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public SignalingMessage save(SignalingMessage entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends SignalingMessage> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(SignalingMessage entity) {
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
  public void deleteAll(Iterable<? extends SignalingMessage> entities) {
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
  public List<SignalingMessage> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<SignalingMessage> findTop50ByRoomIdOrderByCreatedAtDesc(String roomId) {
    return repository.findTop50ByRoomIdOrderByCreatedAtDesc(roomId);
  }

  @Override
  public void deleteByRoomId(String roomId) {
    repository.deleteByRoomId(roomId);
  }

  @Override
  public int deleteByCreatedAtBefore(Instant cutoff) {
    return repository.deleteByCreatedAtBefore(cutoff);
  }
}
