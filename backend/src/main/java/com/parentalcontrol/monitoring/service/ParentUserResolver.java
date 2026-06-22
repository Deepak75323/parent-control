package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ParentUserResolver {

    private final ParentProfileRepository parentProfileRepository;

    public Optional<UUID> resolveParentUserId(Device device) {
        return parentProfileRepository.findUserIdByProfileId(device.getParentProfileId());
    }
}
