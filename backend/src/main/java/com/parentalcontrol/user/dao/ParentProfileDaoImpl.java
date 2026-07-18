package com.parentalcontrol.user.dao;

import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParentProfileDaoImpl implements ParentProfileDao {

  private final ParentProfileRepository repository;

  @Override
  public Optional<ParentProfile> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public ParentProfile save(ParentProfile entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends ParentProfile> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(ParentProfile entity) {
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
  public void deleteAll(Iterable<? extends ParentProfile> entities) {
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
  public List<ParentProfile> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<ParentProfile> findByUser_Id(UUID userId) {
    return repository.findByUser_Id(userId);
  }

  @Override
  public Optional<ParentProfile> findByIdWithUser(UUID id) {
    return repository.findByIdWithUser(id);
  }

  @Override
  public Optional<UUID> findUserIdByProfileId(UUID profileId) {
    return repository.findUserIdByProfileId(profileId);
  }

  @Override
  public Optional<ParentProfile> findByFamilyHandleIgnoreCase(String handle) {
    return repository.findByFamilyHandleIgnoreCase(handle);
  }

  @Override
  public boolean existsByFamilyHandleIgnoreCase(String handle) {
    return repository.existsByFamilyHandleIgnoreCase(handle);
  }

  @Override
  public boolean existsByFamilyHandleIgnoreCaseAndIdNot(String handle, UUID excludeId) {
    return repository.existsByFamilyHandleIgnoreCaseAndIdNot(handle, excludeId);
  }
}
