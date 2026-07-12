package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceAccessService {

  private final DeviceDao deviceRepository;
  private final ParentProfileDao parentProfileRepository;

  @Transactional(readOnly = true)
  public Device requireOwnedDevice(UUID deviceId) {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile parent =
        parentProfileRepository
            .findByUser_Id(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));

    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

    if (!device.getParentProfileId().equals(parent.getId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    return device;
  }

  @Transactional(readOnly = true)
  public Device requireAuthenticatedDevice() {
    UUID deviceId = SecurityUtils.requireDeviceId();
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
    requireMonitoringActive(device);
    return device;
  }

  /** Rejects API calls from revoked devices until the child re-pairs. */
  public void requireMonitoringActive(Device device) {
    if (device.getStatus() == DeviceStatus.REVOKED) {
      throw new BusinessException(
          ErrorCode.REPAIR_REQUIRED, MessageConstants.DEVICE_MONITORING_REVOKED_RE_PAIR_REQUIRED);
    }
  }
}
