package com.parentalcontrol.device.security;

import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.repository.DeviceRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceTokenValidationService {

    private final DeviceRepository deviceRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public void validateDeviceToken(Claims claims) {
        UUID deviceId = jwtTokenProvider.getDeviceId(claims);
        if (deviceId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid device token");
        }

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Device not found"));

        // OFFLINE is presence-only (stale heartbeat). Revocation is REVOKED + tokenVersion mismatch.
        if (device.getStatus() == DeviceStatus.REVOKED) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Device monitoring revoked");
        }
        if (device.getStatus() == DeviceStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Device suspended");
        }

        long tokenVersion = jwtTokenProvider.getTokenVersion(claims);
        if (tokenVersion != device.getTokenVersion()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Device token revoked — re-pair required");
        }
    }
}
