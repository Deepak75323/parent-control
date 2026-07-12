package com.parentalcontrol.config;

import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceTrustLevel;
import com.parentalcontrol.device.service.DeviceTrustObservabilityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Blocks child-device API traffic when persisted trust state is {@link DeviceTrustLevel#BLOCKED} or
 * {@link DeviceTrustLevel#UNTRUSTED} (heartbeat exempt so parents retain visibility).
 */
@Component
@RequiredArgsConstructor
public class DeviceTrustInterceptor implements HandlerInterceptor {

  private static final Set<String> TRUST_EXEMPT = Set.of(ApiConstants.DEVICE_PAIR);

  private static final Set<String> UNTRUSTED_ALLOWED = Set.of(ApiConstants.DEVICE_HEARTBEAT);

  private final DeviceDao deviceRepository;
  private final DeviceTrustProperties deviceTrustProperties;
  private final DeviceTrustObservabilityService observabilityService;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!deviceTrustProperties.isEnforcementEnabled()) {
      return true;
    }
    String path = request.getRequestURI();
    if (TRUST_EXEMPT.contains(path)) {
      return true;
    }

    AuthenticatedUser user = SecurityUtils.currentUser().orElse(null);
    if (user == null || user.getRole() != Role.ROLE_CHILD_DEVICE || user.getDeviceId() == null) {
      return true;
    }

    UUID deviceId = user.getDeviceId();
    Device device = deviceRepository.findById(deviceId).orElse(null);
    if (device == null) {
      return true;
    }

    DeviceTrustLevel level = device.getTrustLevel();
    if (level == DeviceTrustLevel.BLOCKED) {
      observabilityService.recordInterceptorBlocked(level);
      throw new BusinessException(
          ErrorCode.DEVICE_TRUST_DENIED,
          MessageConstants.DEVICE_IS_BLOCKED_DUE_TO_TRUST_VIOLATIONS);
    }
    if (level == DeviceTrustLevel.UNTRUSTED && !UNTRUSTED_ALLOWED.contains(path)) {
      observabilityService.recordInterceptorBlocked(level);
      throw new BusinessException(
          ErrorCode.DEVICE_TRUST_DENIED,
          MessageConstants.DEVICE_TRUST_IS_INSUFFICIENT_FOR_THIS_ACTION);
    }
    return true;
  }
}
