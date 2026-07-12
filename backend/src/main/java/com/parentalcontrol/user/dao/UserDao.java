package com.parentalcontrol.user.dao;

import com.parentalcontrol.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for User. Services depend on this interface; UserDaoImpl delegates to
 * UserRepository.
 */
public interface UserDao {

  Optional<User> findById(UUID id);

  User save(User entity);

  <S extends User> List<S> saveAll(Iterable<S> entities);

  void delete(User entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends User> entities);

  boolean existsById(UUID id);

  long count();

  List<User> findAll();

  void flush();

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);
}
