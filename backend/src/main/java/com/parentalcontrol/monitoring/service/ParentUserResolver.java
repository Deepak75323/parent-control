package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.user.dao.ParentProfileDao;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParentUserResolver {

  private final ParentProfileDao parentProfileRepository;

  public Optional<UUID> resolveParentUserId(Device device) {
    return parentProfileRepository.findUserIdByProfileId(device.getParentProfileId());
  }

  /** Resolve a parent user id from a profile id (e.g. previous owner before re-assign). */
  public Optional<UUID> resolveUserIdByProfileId(UUID parentProfileId) {
    if (parentProfileId == null) {
      return Optional.empty();
    }
    return parentProfileRepository.findUserIdByProfileId(parentProfileId);
  }
}
