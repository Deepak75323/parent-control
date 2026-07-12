package com.parentalcontrol.user.dao;

import com.parentalcontrol.user.domain.ChildProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for ChildProfile. Services depend on this interface; ChildProfileDaoImpl
 * delegates to ChildProfileRepository.
 */
public interface ChildProfileDao {

  Optional<ChildProfile> findById(UUID id);

  ChildProfile save(ChildProfile entity);

  <S extends ChildProfile> List<S> saveAll(Iterable<S> entities);

  void delete(ChildProfile entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends ChildProfile> entities);

  boolean existsById(UUID id);

  long count();

  List<ChildProfile> findAll();

  void flush();

  List<ChildProfile> findByParentProfileId(UUID parentProfileId);
}
