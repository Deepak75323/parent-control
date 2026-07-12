package com.parentalcontrol.device.security;

import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceTokenValidationService {

  private final DeviceDao deviceRepository;
  private final JwtTokenProvider jwtTokenProvider;

  public void validateDeviceToken(Claims claims) {
    UUID deviceId = jwtTokenProvider.getDeviceId(claims);
    if (deviceId == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_DEVICE_TOKEN);
    }

    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.UNAUTHORIZED, MessageConstants.DEVICE_NOT_FOUND));

    // OFFLINE is presence-only (stale heartbeat). Revocation is REVOKED + tokenVersion mismatch.
    if (device.getStatus() == DeviceStatus.REVOKED) {
      throw new BusinessException(
          ErrorCode.REPAIR_REQUIRED, MessageConstants.DEVICE_MONITORING_REVOKED_RE_PAIR_REQUIRED);
    }
    if (device.getStatus() == DeviceStatus.SUSPENDED) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, MessageConstants.DEVICE_SUSPENDED);
    }

    long tokenVersion = jwtTokenProvider.getTokenVersion(claims);
    if (tokenVersion != device.getTokenVersion()) {
      throw new BusinessException(
          ErrorCode.REPAIR_REQUIRED, MessageConstants.DEVICE_TOKEN_REVOKED_RE_PAIR_REQUIRED);
    }
  }
}
