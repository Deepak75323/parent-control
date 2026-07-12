package com.parentalcontrol.user.service;

import com.parentalcontrol.auth.dao.RefreshTokenDao;
import com.parentalcontrol.auth.service.UserTokenInvalidationService;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.policy.dao.PolicyDao;
import com.parentalcontrol.user.dao.ChildProfileDao;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.PersonalDataPurgeDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

  private final UserDao userRepository;
  private final ParentProfileDao parentProfileRepository;
  private final ChildProfileDao childProfileRepository;
  private final DeviceDao deviceRepository;
  private final PolicyDao policyRepository;
  private final PersonalDataPurgeDao personalDataPurgeRepository;
  private final RefreshTokenDao refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserTokenInvalidationService userTokenInvalidationService;
  private final SubscriptionService subscriptionService;

  @Transactional
  public void deleteCurrentAccount(String password) {
    UUID userId = SecurityUtils.requireUserId();
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new BusinessException(
          ErrorCode.INVALID_CREDENTIALS, MessageConstants.INCORRECT_PASSWORD);
    }

    ParentProfile parent =
        parentProfileRepository
            .findByUser_Id(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));

    List<Device> devices =
        deviceRepository.findByParentProfileIdOrderByCreatedAtDesc(parent.getId());
    List<UUID> deviceIds = devices.stream().map(Device::getId).toList();

    subscriptionService.cancelProviderSubscriptionForParent(parent.getId());

    if (!deviceIds.isEmpty()) {
      personalDataPurgeRepository.deleteNotificationLogs(deviceIds);
      personalDataPurgeRepository.deleteLocationHistory(deviceIds);
      personalDataPurgeRepository.deleteAppUsageLogs(deviceIds);
      personalDataPurgeRepository.deleteInstalledApps(deviceIds);
      personalDataPurgeRepository.deleteAlerts(deviceIds);
      devices.forEach(deviceRepository::delete);
    }

    personalDataPurgeRepository.deleteEmailVerificationTokens(userId);
    personalDataPurgeRepository.deletePairingCodes(parent.getId());
    personalDataPurgeRepository.deleteSubscriptionsAndPayments(parent.getId());
    personalDataPurgeRepository.deleteAuditLogsForUser(userId);

    policyRepository
        .findByParentProfileIdOrderByUpdatedAtDesc(parent.getId())
        .forEach(policyRepository::delete);

    childProfileRepository
        .findByParentProfileId(parent.getId())
        .forEach(childProfileRepository::delete);

    parent.setFirstName("Deleted");
    parent.setLastName("User");
    parent.setPhone(null);
    parent.setDefaultChildLoginPasswordHash(null);
    parentProfileRepository.delete(parent);

    userTokenInvalidationService.invalidateAllSessions(userId);

    user.setEmail("deleted-" + user.getId() + "@anonymized.invalid");
    user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
    user.setParentPinHash(null);
    user.setSilentStreamPinUnlocked(false);
    userRepository.delete(user);

    log.info("Account deleted and personal data purged userId={}", userId);
  }
}
