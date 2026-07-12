package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.StreamingPolicyRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for StreamingPolicyRule. Services depend on this interface;
 * StreamingPolicyRuleDaoImpl delegates to StreamingPolicyRuleRepository.
 */
public interface StreamingPolicyRuleDao {

  Optional<StreamingPolicyRule> findById(UUID id);

  StreamingPolicyRule save(StreamingPolicyRule entity);

  <S extends StreamingPolicyRule> List<S> saveAll(Iterable<S> entities);

  void delete(StreamingPolicyRule entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends StreamingPolicyRule> entities);

  boolean existsById(UUID id);

  long count();

  List<StreamingPolicyRule> findAll();

  void flush();

  Optional<StreamingPolicyRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
