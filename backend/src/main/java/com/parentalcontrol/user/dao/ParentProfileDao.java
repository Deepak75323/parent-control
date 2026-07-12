package com.parentalcontrol.user.dao;

import com.parentalcontrol.user.domain.ParentProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for ParentProfile. Services depend on this interface; ParentProfileDaoImpl
 * delegates to ParentProfileRepository.
 */
public interface ParentProfileDao {

  Optional<ParentProfile> findById(UUID id);

  ParentProfile save(ParentProfile entity);

  <S extends ParentProfile> List<S> saveAll(Iterable<S> entities);

  void delete(ParentProfile entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends ParentProfile> entities);

  boolean existsById(UUID id);

  long count();

  List<ParentProfile> findAll();

  void flush();

  Optional<ParentProfile> findByUser_Id(UUID userId);

  Optional<ParentProfile> findByIdWithUser(UUID id);

  Optional<UUID> findUserIdByProfileId(UUID profileId);
}
