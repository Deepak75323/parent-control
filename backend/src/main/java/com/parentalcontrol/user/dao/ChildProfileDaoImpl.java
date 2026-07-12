package com.parentalcontrol.user.dao;

import com.parentalcontrol.user.domain.ChildProfile;
import com.parentalcontrol.user.repository.ChildProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChildProfileDaoImpl implements ChildProfileDao {

  private final ChildProfileRepository repository;

  @Override
  public Optional<ChildProfile> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public ChildProfile save(ChildProfile entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends ChildProfile> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(ChildProfile entity) {
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
  public void deleteAll(Iterable<? extends ChildProfile> entities) {
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
  public List<ChildProfile> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<ChildProfile> findByParentProfileId(UUID parentProfileId) {
    return repository.findByParentProfileId(parentProfileId);
  }
}
