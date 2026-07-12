package com.parentalcontrol.user.dao;

import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserDaoImpl implements UserDao {

  private final UserRepository repository;

  @Override
  public Optional<User> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public User save(User entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends User> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(User entity) {
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
  public void deleteAll(Iterable<? extends User> entities) {
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
  public List<User> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<User> findByEmailIgnoreCase(String email) {
    return repository.findByEmailIgnoreCase(email);
  }

  @Override
  public boolean existsByEmailIgnoreCase(String email) {
    return repository.existsByEmailIgnoreCase(email);
  }
}
